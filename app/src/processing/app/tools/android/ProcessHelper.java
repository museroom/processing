/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */
/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2009-10 Ben Fry and Casey Reas

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License version 2
 as published by the Free Software Foundation.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package processing.app.tools.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.concurrent.CountDownLatch;
import processing.core.PApplet;

/**
 * Class to handle calling Runtime.exec() and stuffing output and error streams
 * into String lists that can be dealt with more easily.
 *
 * @author Jonathan Feinberg <jdf@pobox.com>
 */
class ProcessHelper {
  private final String[] cmd;
  private final StringWriter stdout = new StringWriter();
  private final StringWriter stderr = new StringWriter();

  public ProcessHelper(final String... cmd) throws IOException {
    this.cmd = cmd;
  }

  public String getCommand() {
    return PApplet.join(cmd, " ");
  }

  /**
   * Blocking execution.
   * @return exit value of process
   * @throws InterruptedException
   * @throws IOException
   */
  public int execute() throws InterruptedException, IOException {
    return execute(false);
  }

  /**
   * Blocking execution.
   * @param tee Send process's stdout/stderr to stdout/stderr in addition to capturing them
   * @return exit value of process
   * @throws InterruptedException
   * @throws IOException
   */
  public int execute(final boolean tee) throws InterruptedException,
      IOException {
    return execute(tee, tee);
  }

  /**
   * Blocking execution.
   * @param teeOut Send process's stdout to stdout in addition to capturing it
   * @param teeErr Send process's stderr to stderr in addition to capturing it
   * @return exit value of process
   * @throws InterruptedException
   * @throws IOException
   */
  public int execute(final boolean teeOut, final boolean teeErr)
      throws InterruptedException, IOException {
    final long startTime = System.currentTimeMillis();

    System.err.println("Executing " + PApplet.join(cmd, ' '));
    final Process process = Runtime.getRuntime().exec(cmd);
    // The latch is decremented by the StringRedirectingThread when it exhausts its stream
    final CountDownLatch latch = new CountDownLatch(2);
    if (teeOut) {
      new StringRedirectThread(process.getInputStream(), latch, stdout,
                               new OutputStreamWriter(System.out)).start();
    } else {
      new StringRedirectThread(process.getInputStream(), latch, stdout).start();
    }
    if (teeErr) {
      new StringRedirectThread(process.getErrorStream(), latch, stderr,
                               new OutputStreamWriter(System.err)).start();
    } else {
      new StringRedirectThread(process.getErrorStream(), latch, stderr).start();
    }
    latch.await();
    System.err.println((System.currentTimeMillis() - startTime) + "ms: "
        + PApplet.join(cmd, " "));
    return process.waitFor();
  }

  public String getStderr() {
    return stderr.toString();
  }

  public String getStdout() {
    return stdout.toString();
  }

  public void dump() {
    System.out.println(getStdout());
    System.err.println(getStderr());
  }

  static class StringRedirectThread extends Thread {
    private final BufferedReader reader;
    private final PrintWriter[] outs;
    private final CountDownLatch latch;

    public StringRedirectThread(final InputStream in,
                                final CountDownLatch latch,
                                final Writer... targetWriters) {
      this.reader = new BufferedReader(new InputStreamReader(in));
      this.latch = latch;
      this.outs = new PrintWriter[targetWriters.length];
      for (int i = 0; i < outs.length; i++) {
        outs[i] = new PrintWriter(targetWriters[i], true);
      }
    }

    @Override
    public void run() {
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.trim().length() == 0) {
            continue;
          }
          for (final PrintWriter out : outs) {
            out.println(line);
          }
        }
      } catch (final IOException e) {
        e.printStackTrace(System.err);
      } finally {
        latch.countDown();
      }
    }
  }
}