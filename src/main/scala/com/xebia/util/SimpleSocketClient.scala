package com.xebia.util

import java.io._
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{Channels, AsynchronousSocketChannel}

object SocketClientTester extends App {
  //  val msg = """<14>1 2012-10-08T11:31:30.379+02:00 lsrv1298.linux.rabobank.nl SAM - samRequestResponse [sam_requestresponse@9211 timestamp="2012-10-08 09:31:28.808 +0000" url="http://sam-sit.rf.rabobank.nl/klanten-t/qslo.htm?Abs-Pad=klanten%2Dt%2Foverzicht_ongetekende_opdrachten%2Ehtml%2Fsummary%2Edo" action="GET" sessionId="6543217413eix03b6EvT3843tLYTk45" functionName="200" user="KLID5185202800000869500001" ipAddress="90.153.241.128" reqLevel="" currentLevel="3" distrChannel="PIF" host="rass-sit.rf.rabobank.nl:14721" signInd="0" reverseProxy="https://10.233.221.107:14722" cookieString="QTSTBANS=62d2oPpsaPeix03b6EvTPhdUstLYTk45, KLANTINFO=INITIALS%3DE%2E%26NAMEPREFIX%3Dvan%26SHORTNAME%3DNiels%20PRO%201%281143%20%29%26JURNAME%3DNiels%20PRO%201%20%281143%20%29%26BRANCH%3DRabobank%20Hoogmade%26DATETIME%3D2012%2D10%2D08T09%3A31%3A13Z, RABOINFO=1fbf44b0f627ed3bb062af73ec58bff4ba63841553525256545657505953585d37575b41434141, BKC=VRS%2DID%3DBRIT0004%26SITE%2DID%3DXTSTBAN1%26SESSIE%2DID%3D212216448671903368%26KLANT%2DID%3DKLID51873984000001143000000000001143ZEJ94%26KNMK%2DTS%3D212216448672969793%26NIV%3D3%26VIA%3DSI%26SECURE%2DIND%3DJ%26AUTHMETH%3D02%26AUT%2DVSTRK%2DPCT%3D66%26IP%2DADR%3D10%2E233%2E78%2E194%26RISC%2DCNFG%2DID%3DAS10W001%26TR%3DN%26WM%3DBD19BC5A%26KLANT%2DTYP%3DZ%26KLANT%2DROL%3DE%26KLANT%2DINDL%3D94, SAM=4995853f8f814ed199087cb541502cfd|123280001|1234||||1||||||||" userAgent="Mozilla/5.0 (Windows NT 5.1; rv:12.0) Gecko/20100101 Firefox/12.0" referer="https://rass-sit.rf.rabobank.nl:14721/klanten-t/overzicht_ongetekende_opdrachten.html" postData="-" s_timestamp="2012-10-08 09:31:30.379 +0000" s_responseStatus="200" s_user="" s_authResult="10" s_authLevel="" s_secProfile="" s_prevSession="" s_registerResult="" s_ticket="" s_signInd="1" s_endSession="" s_cookieString="RaboTS=YCc9TP08mkmap9ROzlSOPw==; Version=1; Path=/, SAM=aecc057cacea440690605efba707fefd|123280001|1234|BRIT0004%257COI1349688688796%257C0%257CEUR%257C2%257Cklanten%252Dt%25252Foverzicht%255Fongetekende%255Fopdrachten%252Ehtml%25252Fsigneren%252Edo%257CJ%257C02%257Cklanten%25252Dt%25252Foverzicht%255Fongetekende%255Fopdrachten%25252Ehtml%25252Fsigneren%25252Edo%257C%257C%257C%257CTe%2520verzenden%2520opdrachten%257C2%257C|89e8618b64dc372d5937a2ac49e0adc7db07e2a3||1||||||||"]"""
  try {
    val to = 10
    val tester = new SimpleSocketClient(port = 6000)
    //tester.sendAndForget("reset\n")
    val (elapsed, _) = measure {
      tester.sendAndReceive(to)
    }
    println(s"=====================> Total sent: $to, elapsed $elapsed ms, tps ${to.toDouble / elapsed * 1000}")
    tester.close()
  } catch {
    case e: Throwable ⇒ e.printStackTrace()
  }

  private def measure[T](callback: ⇒ T): (Long, T) = {
    val start = System.currentTimeMillis
    val res = callback
    val elapsed = System.currentTimeMillis - start
    (elapsed, res)
  }
}

class SocketReadException extends Exception

class SimpleSocketClient(val host: String = "localhost", val port: Int = 11111) {
  val asyncSocketChannel = {
    val adr = new InetSocketAddress(host, port)

    val asc: AsynchronousSocketChannel = AsynchronousSocketChannel.open()

    // wait for connection
    asc.connect(adr).get()
    asc
  }

  def sendAndReceive(times: Int = 1) = {
    try {
      1 to times foreach (i => {
        val msg = s"msg: $i"
        val msgBuffer: ByteBuffer = ByteBuffer.wrap((msg + "\n").getBytes("utf-8"))
        println(s"writing $msg")
        asyncSocketChannel.write(msgBuffer)
      })
      val in = new BufferedReader(new InputStreamReader(Channels.newInputStream(asyncSocketChannel), "utf-8"))
      1 to times foreach (_ => {
        val line = in.readLine()
        println("reading: " + line)
      })
    } catch {
      case e: Throwable ⇒
        println(e.getClass.getName + " " + e.getMessage)
        throw e
    }
  }

  def close() = asyncSocketChannel.close()

}