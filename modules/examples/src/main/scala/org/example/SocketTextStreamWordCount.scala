package org.example

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.flinkx.api.*
import org.apache.flinkx.api.serializers.*
import org.apache.flink.configuration.Configuration
import org.apache.flink.configuration.ConfigConstants
import org.apache.flink.configuration.RestOptions.BIND_PORT
import scala.jdk.CollectionConverters.*

/** This example shows an implementation of WordCount with data from a text socket. To run the example make sure that
  * the service providing the text data is already up and running.
  *
  * To start an example socket text stream on your local machine run netcat from a command line, where the parameter
  * specifies the port number:
  *
  * {{{
  *   nc -lk 9999
  * }}}
  *
  * Usage:
  * {{{
  *   SocketTextStreamWordCount <hostname> <port> <output path>
  * }}}
  *
  * This example shows how to:
  *
  *   - use StreamExecutionEnvironment.socketTextStream
  *   - write a simple Flink Streaming program in scala.
  *   - write and use user-defined functions.
  */
@main def SocketTextStreamWordCount(hostName: String, port: Int) =
  val config = Configuration.fromMap(
    Map(
//      ConfigConstants.LOCAL_START_WEBSERVER -> "true",
      BIND_PORT.key -> "8080"
    ).asJava
  )
  val flink = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(config)

  flink
    .socketTextStream(hostName, port)
    .flatMap(_.toLowerCase.split("\\W+").filter(_.nonEmpty))
    .map((_, 1))
    .keyBy(_._1)
    .sum(1)
    .print()

  flink.execute("Scala SocketTextStreamWordCount Example")
