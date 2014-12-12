package com.xebia.kafka

package object decoders {

  import akka.util.ByteString
  import kafka.serializer.Decoder

  object ByteStringDecoder extends Decoder[ByteString] {
    override def fromBytes(bytes: Array[Byte]) = ByteString(bytes)
  }

  import com.google.common.primitives.Longs
  import kafka.serializer.Decoder

  object LongDecoder extends Decoder[Long] {
    override def fromBytes(bytes: Array[Byte]): Long = Longs.fromByteArray(bytes)
  }
}
