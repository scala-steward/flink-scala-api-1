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

import org.apache.flinkx.api.OutputTag
import org.apache.flinkx.api.function.{ProcessAllWindowFunction, ProcessWindowFunction}
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.streaming.api.functions.windowing.{
  ProcessAllWindowFunction => JProcessAllWindowFunction,
  ProcessWindowFunction => JProcessWindowFunction
}
import org.apache.flink.streaming.api.windowing.windows.Window
import org.apache.flink.util.Collector

import scala.jdk.CollectionConverters._

/** A wrapper function that exposes a Scala ProcessWindowFunction as a ProcessWindowFunction function.
  *
  * The Scala and Java Window functions differ in their type of "Iterable":
  *   - Scala WindowFunction: scala.Iterable
  *   - Java WindowFunction: java.lang.Iterable
  */
final class ScalaProcessWindowFunctionWrapper[IN, OUT, KEY, W <: Window](
    private[this] val func: ProcessWindowFunction[IN, OUT, KEY, W]
) extends JProcessWindowFunction[IN, OUT, KEY, W] {

  override def process(
      key: KEY,
      context: JProcessWindowFunction[IN, OUT, KEY, W]#Context,
      elements: java.lang.Iterable[IN],
      out: Collector[OUT]
  ): Unit = {
    val ctx = new func.Context {
      override def window = context.window

      override def currentProcessingTime = context.currentProcessingTime

      override def currentWatermark = context.currentWatermark

      override def windowState = context.windowState()

      override def globalState = context.globalState()

      override def output[X](outputTag: OutputTag[X], value: X) = context.output(outputTag, value)
    }
    func.process(key, ctx, elements.asScala, out)
  }

  override def clear(context: JProcessWindowFunction[IN, OUT, KEY, W]#Context): Unit = {
    val ctx = new func.Context {
      override def window = context.window

      override def currentProcessingTime = context.currentProcessingTime

      override def currentWatermark = context.currentWatermark

      override def windowState = context.windowState()

      override def globalState = context.globalState()

      override def output[X](outputTag: OutputTag[X], value: X) = context.output(outputTag, value)
    }
    func.clear(ctx)
  }

  override def setRuntimeContext(t: RuntimeContext): Unit = {
    super.setRuntimeContext(t)
    func match {
      case rfunc: ProcessWindowFunction[IN, OUT, KEY, W] => rfunc.setRuntimeContext(t)
      case null                                          =>
    }
  }

  override def open(context: OpenContext): Unit = {
    super.open(context)
    func match {
      case rfunc: ProcessWindowFunction[IN, OUT, KEY, W] => rfunc.open(context)
      case null                                          =>
    }
  }

  override def close(): Unit = {
    super.close()
    func match {
      case rfunc: ProcessWindowFunction[IN, OUT, KEY, W] => rfunc.close()
      case null                                          =>
    }
  }
}

/** A wrapper function that exposes a Scala ProcessWindowFunction as a ProcessWindowFunction function.
  *
  * The Scala and Java Window functions differ in their type of "Iterable":
  *   - Scala WindowFunction: scala.Iterable
  *   - Java WindowFunction: java.lang.Iterable
  */
final class ScalaProcessAllWindowFunctionWrapper[IN, OUT, W <: Window](
    private[this] val func: ProcessAllWindowFunction[IN, OUT, W]
) extends JProcessAllWindowFunction[IN, OUT, W] {

  override def process(
      context: JProcessAllWindowFunction[IN, OUT, W]#Context,
      elements: java.lang.Iterable[IN],
      out: Collector[OUT]
  ): Unit = {
    val ctx = new func.Context {
      override def window = context.window

      override def windowState = context.windowState()

      override def globalState = context.globalState()

      override def output[X](outputTag: OutputTag[X], value: X) = context.output(outputTag, value)
    }
    func.process(ctx, elements.asScala, out)
  }

  override def clear(context: JProcessAllWindowFunction[IN, OUT, W]#Context): Unit = {
    val ctx = new func.Context {
      override def window = context.window

      override def windowState = context.windowState()

      override def globalState = context.globalState()

      override def output[X](outputTag: OutputTag[X], value: X) = context.output(outputTag, value)
    }
    func.clear(ctx)
  }

  override def setRuntimeContext(t: RuntimeContext): Unit = {
    super.setRuntimeContext(t)
    func match {
      case rfunc: ProcessAllWindowFunction[IN, OUT, W] => rfunc.setRuntimeContext(t)
      case null                                        =>
    }
  }

  override def open(context: OpenContext): Unit = {
    super.open(context)
    func match {
      case rfunc: ProcessAllWindowFunction[IN, OUT, W] => rfunc.open(context)
      case null                                        =>
    }
  }

  override def close(): Unit = {
    super.close()
    func match {
      case rfunc: ProcessAllWindowFunction[IN, OUT, W] => rfunc.close()
      case null                                        =>
    }
  }
}
