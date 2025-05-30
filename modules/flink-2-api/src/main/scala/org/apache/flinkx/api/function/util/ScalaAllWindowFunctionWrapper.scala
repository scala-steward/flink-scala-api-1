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

package org.apache.flinkx.api.function.util

import org.apache.flink.api.common.functions.{IterationRuntimeContext, RichFunction, RuntimeContext}
import org.apache.flink.api.common.functions.WrappingFunction
import org.apache.flink.streaming.api.functions.windowing.{AllWindowFunction => JAllWindowFunction}
import org.apache.flinkx.api.function.AllWindowFunction
import org.apache.flink.streaming.api.windowing.windows.Window
import org.apache.flink.util.Collector

import scala.jdk.CollectionConverters._

/** A wrapper function that exposes a Scala WindowFunction as a JavaWindow function.
  *
  * The Scala and Java Window functions differ in their type of "Iterable":
  *   - Scala WindowFunction: scala.Iterable
  *   - Java WindowFunction: java.lang.Iterable
  */
final class ScalaAllWindowFunctionWrapper[IN, OUT, W <: Window](func: AllWindowFunction[IN, OUT, W])
    extends WrappingFunction[AllWindowFunction[IN, OUT, W]](func)
    with JAllWindowFunction[IN, OUT, W]
    with RichFunction {

  @throws(classOf[Exception])
  override def apply(window: W, input: java.lang.Iterable[IN], out: Collector[OUT]) = {
    wrappedFunction.apply(window, input.asScala, out)
  }

  override def getRuntimeContext: RuntimeContext = {
    throw new RuntimeException("This should never be called")
  }

  override def getIterationRuntimeContext: IterationRuntimeContext = {
    throw new RuntimeException("This should never be called")
  }
}
