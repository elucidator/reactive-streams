package com.xebia.util

import java.io.{PrintWriter, OutputStreamWriter, BufferedReader, InputStreamReader}
import java.net.{Socket, ServerSocket}

object LatencyEchoServer extends App {
  private val serverSocket: ServerSocket = new ServerSocket(11111)
  var threadcount = 0
  while (true) {
    new EchoThread(serverSocket.accept(), threadcount).start()
    threadcount += 1
  }

}

class EchoThread(socket: Socket, threadcount: Int) extends Thread {
  val delay = 1000
  var counter = 0

  override def run(): Unit = {
    println(s"Remote address $socket connected")

    val out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream, "utf-8"), true)
    val in = new BufferedReader(new InputStreamReader(socket.getInputStream, "utf-8"))

    var line: String = null
    do {
      line = in.readLine()
      if (line == null) {
        socket.close()
      } else {
        Thread.sleep(delay)
        out.write(s"$threadcount.$counter: $line\n")
        out.flush()
        counter += 1
      }
    } while (line != null)
    println(s"Remote address $socket disconnected")
  }
}

