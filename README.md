Simple run
==========

1. Run LatencyEchoServer
2. Run ParallelProxy
3. Run SimpleSocketClient

Now you can change the number or parallel connections the proxy used by editing 
the numberOfConnections in ParallelProxy.scala

The problem
============
It is expected that the SimpleSocketClient will run faster when there are more parallel connections.
For some unknown the number of connections doesn't influence the transactions per second at all.
