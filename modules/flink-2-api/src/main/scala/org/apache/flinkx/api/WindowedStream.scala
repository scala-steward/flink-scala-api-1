package org.apache.flinkx.api

import org.apache.flinkx.api.function.{ProcessWindowFunction, WindowFunction}
import org.apache.flinkx.api.function.util.{
  ScalaProcessWindowFunctionWrapper,
  ScalaReduceFunction,
  ScalaWindowFunction,
  ScalaWindowFunctionWrapper
}
import org.apache.flink.annotation.{Public, PublicEvolving}
import org.apache.flink.api.common.functions.{AggregateFunction, ReduceFunction}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.{WindowedStream => JavaWStream}
import org.apache.flink.streaming.api.functions.aggregation.AggregationFunction.AggregationType
import org.apache.flink.streaming.api.functions.aggregation.{ComparableAggregator, SumAggregator}
import org.apache.flink.streaming.api.windowing.evictors.Evictor
import org.apache.flink.streaming.api.windowing.triggers.Trigger
import org.apache.flink.streaming.api.windowing.windows.Window
import org.apache.flink.util.Collector
import ScalaStreamOps._

import java.time.Duration

/** A [[WindowedStream]] represents a data stream where elements are grouped by key, and for each key, the stream of
  * elements is split into windows based on a [[org.apache.flink.streaming.api.windowing.assigners.WindowAssigner]].
  * Window emission is triggered based on a [[Trigger]].
  *
  * The windows are conceptually evaluated for each key individually, meaning windows can trigger at different points
  * for each key.
  *
  * If an [[org.apache.flink.streaming.api.windowing.evictors.Evictor]] is specified it will be used to evict elements
  * from the window after evaluation was triggered by the [[Trigger]] but before the actual evaluation of the window.
  * When using an evictor window performance will degrade significantly, since pre-aggregation of window results cannot
  * be used.
  *
  * Note that the [[WindowedStream]] is purely and API construct, during runtime the [[WindowedStream]] will be
  * collapsed together with the [[KeyedStream]] and the operation over the window into one single operation.
  *
  * @tparam T
  *   The type of elements in the stream.
  * @tparam K
  *   The type of the key by which elements are grouped.
  * @tparam W
  *   The type of [[Window]] that the [[org.apache.flink.streaming.api.windowing.assigners.WindowAssigner]] assigns the
  *   elements to.
  */
@Public
class WindowedStream[T, K, W <: Window](javaStream: JavaWStream[T, K, W]) {

  /** Sets the allowed lateness to a user-specified value. If not explicitly set, the allowed lateness is [[0L]].
    * Setting the allowed lateness is only valid for event-time windows. If a value different than 0 is provided with a
    * processing-time [[org.apache.flink.streaming.api.windowing.assigners.WindowAssigner]], then an exception is
    * thrown.
    */
  @PublicEvolving
  def allowedLateness(lateness: Duration): WindowedStream[T, K, W] = {
    javaStream.allowedLateness(lateness)
    this
  }

  /** Send late arriving data to the side output identified by the given [[OutputTag]]. Data is considered late after
    * the watermark has passed the end of the window plus the allowed lateness set using [[allowedLateness(Time)]].
    *
    * You can get the stream of late data using [[DataStream.getSideOutput()]] on the [[DataStream]] resulting from the
    * windowed operation with the same [[OutputTag]].
    */
  @PublicEvolving
  def sideOutputLateData(outputTag: OutputTag[T]): WindowedStream[T, K, W] = {
    javaStream.sideOutputLateData(outputTag)
    this
  }

  /** Sets the [[Trigger]] that should be used to trigger window emission.
    */
  @PublicEvolving
  def trigger(trigger: Trigger[_ >: T, _ >: W]): WindowedStream[T, K, W] = {
    javaStream.trigger(trigger)
    this
  }

  /** Sets the [[Evictor]] that should be used to evict elements from a window before emission.
    *
    * Note: When using an evictor window performance will degrade significantly, since pre-aggregation of window results
    * cannot be used.
    */
  @PublicEvolving
  def evictor(evictor: Evictor[_ >: T, _ >: W]): WindowedStream[T, K, W] = {
    javaStream.evictor(evictor)
    this
  }

  // ------------------------------------------------------------------------
  //  Operations on the keyed windows
  // ------------------------------------------------------------------------

  // --------------------------- reduce() -----------------------------------

  /** Applies a reduce function to the window. The window function is called for each evaluation of the window for each
    * key individually. The output of the reduce function is interpreted as a regular non-windowed stream.
    *
    * This window will try and pre-aggregate data as much as the window policies permit. For example, tumbling time
    * windows can perfectly pre-aggregate the data, meaning that only one element per key is stored. Sliding time
    * windows will pre-aggregate on the granularity of the slide interval, so a few elements are stored per key (one per
    * slide interval). Custom windows may not be able to pre-aggregate, or may need to store extra values in an
    * aggregation tree.
    *
    * @param function
    *   The reduce function.
    * @return
    *   The data stream that is the result of applying the reduce function to the window.
    */
  def reduce(function: ReduceFunction[T]): DataStream[T] = {
    asScalaStream(javaStream.reduce(clean(function)))
  }

  /** Applies a reduce function to the window. The window function is called for each evaluation of the window for each
    * key individually. The output of the reduce function is interpreted as a regular non-windowed stream.
    *
    * This window will try and pre-aggregate data as much as the window policies permit. For example, tumbling time
    * windows can perfectly pre-aggregate the data, meaning that only one element per key is stored. Sliding time
    * windows will pre-aggregate on the granularity of the slide interval, so a few elements are stored per key (one per
    * slide interval). Custom windows may not be able to pre-aggregate, or may need to store extra values in an
    * aggregation tree.
    *
    * @param function
    *   The reduce function.
    * @return
    *   The data stream that is the result of applying the reduce function to the window.
    */
  def reduce(function: (T, T) => T): DataStream[T] = {
    if (function == null) {
      throw new NullPointerException("Reduce function must not be null.")
    }
    val cleanFun = clean(function)
    val reducer  = new ScalaReduceFunction[T](cleanFun)
    reduce(reducer)
  }

  /** Applies the given window function to each window. The window function is called for each evaluation of the window
    * for each key individually. The output of the window function is interpreted as a regular non-windowed stream.
    *
    * Arriving data is pre-aggregated using the given pre-aggregation reducer.
    *
    * @param preAggregator
    *   The reduce function that is used for pre-aggregation
    * @param function
    *   The window function.
    * @return
    *   The data stream that is the result of applying the window function to the window.
    */
  def reduce[R: TypeInformation](
      preAggregator: ReduceFunction[T],
      function: WindowFunction[T, R, K, W]
  ): DataStream[R] = {

    val cleanedPreAggregator  = clean(preAggregator)
    val cleanedWindowFunction = clean(function)

    val applyFunction = new ScalaWindowFunctionWrapper[T, R, K, W](cleanedWindowFunction)

    val resultType: TypeInformation[R] = implicitly[TypeInformation[R]]
    asScalaStream(javaStream.reduce(cleanedPreAggregator, applyFunction, resultType))
  }

  /** Applies the given window function to each window. The window function is called for each evaluation of the window
    * for each key individually. The output of the window function is interpreted as a regular non-windowed stream.
    *
    * Arriving data is pre-aggregated using the given pre-aggregation reducer.
    *
    * @param preAggregator
    *   The reduce function that is used for pre-aggregation
    * @param windowFunction
    *   The window function.
    * @return
    *   The data stream that is the result of applying the window function to the window.
    */
  def reduce[R: TypeInformation](
      preAggregator: (T, T) => T,
      windowFunction: (K, W, Iterable[T], Collector[R]) => Unit
  ): DataStream[R] = {

    if (preAggregator == null) {
      throw new NullPointerException("Reduce function must not be null.")
    }
    if (windowFunction == null) {
      throw new NullPointerException("WindowApply function must not be null.")
    }

    val cleanReducer        = clean(preAggregator)
    val cleanWindowFunction = clean(windowFunction)

    val reducer       = new ScalaReduceFunction[T](cleanReducer)
    val applyFunction = new ScalaWindowFunction[T, R, K, W](cleanWindowFunction)

    asScalaStream(javaStream.reduce(reducer, applyFunction, implicitly[TypeInformation[R]]))
  }

  /** Applies the given reduce function to each window. The window reduced value is then passed as input of the window
    * function. The output of the window function is interpreted as a regular non-windowed stream.
    *
    * @param preAggregator
    *   The reduce function that is used for pre-aggregation
    * @param function
    *   The process window function.
    * @return
    *   The data stream that is the result of applying the window function to the window.
    */
  @PublicEvolving
  def reduce[R: TypeInformation](
      preAggregator: (T, T) => T,
      function: ProcessWindowFunction[T, R, K, W]
  ): DataStream[R] = {

    val cleanedPreAggregator  = clean(preAggregator)
    val cleanedWindowFunction = clean(function)

    val reducer       = new ScalaReduceFunction[T](cleanedPreAggregator)
    val applyFunction = new ScalaProcessWindowFunctionWrapper[T, R, K, W](cleanedWindowFunction)

    val resultType: TypeInformation[R] = implicitly[TypeInformation[R]]
    asScalaStream(javaStream.reduce(reducer, applyFunction, resultType))
  }

  /** Applies the given reduce function to each window. The window reduced value is then passed as input of the window
    * function. The output of the window function is interpreted as a regular non-windowed stream.
    *
    * @param preAggregator
    *   The reduce function that is used for pre-aggregation
    * @param function
    *   The process window function.
    * @return
    *   The data stream that is the result of applying the window function to the window.
    */
  @PublicEvolving
  def reduce[R: TypeInformation](
      preAggregator: ReduceFunction[T],
      function: ProcessWindowFunction[T, R, K, W]
  ): DataStream[R] = {

    val cleanedPreAggregator  = clean(preAggregator)
    val cleanedWindowFunction = clean(function)

    val applyFunction = new ScalaProcessWindowFunctionWrapper[T, R, K, W](cleanedWindowFunction)

    val resultType: TypeInformation[R] = implicitly[TypeInformation[R]]
    asScalaStream(javaStream.reduce(cleanedPreAggregator, applyFunction, resultType))
  }

  // -------------------------- aggregate() ---------------------------------

  /** Applies the given aggregation function to each window and key. The aggregation function is called for each
    * element, aggregating values incrementally and keeping the state to one accumulator per key and window.
    *
    * @param aggregateFunction
    *   The aggregation function.
    * @return
    *   The data stream that is the result of applying the fold function to the window.
    */
  @PublicEvolving
  def aggregate[ACC: TypeInformation, R: TypeInformation](
      aggregateFunction: AggregateFunction[T, ACC, R]
  ): DataStream[R] = {

    val accumulatorType: TypeInformation[ACC] = implicitly[TypeInformation[ACC]]
    val resultType: TypeInformation[R]        = implicitly[TypeInformation[R]]

    asScalaStream(javaStream.aggregate(clean(aggregateFunction), accumulatorType, resultType))
  }

  /** Applies the given window function to each window. The window function is called for each evaluation of the window
    * for each key individually. The output of the window function is interpreted as a regular non-windowed stream.
    *
    * Arriving data is pre-aggregated using the given aggregation function.
    *
    * @param preAggregator
    *   The aggregation function that is used for pre-aggregation
    * @param windowFunction
    *   The window function.
    * @return
    *   The data stream that is the result of applying the window function to the window.
    */
  @PublicEvolving
  def aggregate[ACC: TypeInformation, V: TypeInformation, R: TypeInformation](
      preAggregator: AggregateFunction[T, ACC, V],
      windowFunction: WindowFunction[V, R, K, W]
  ): DataStream[R] = {

    val cleanedPreAggregator  = clean(preAggregator)
    val cleanedWindowFunction = clean(windowFunction)

    val applyFunction = new ScalaWindowFunctionWrapper[V, R, K, W](cleanedWindowFunction)

    val accumulatorType: TypeInformation[ACC] = implicitly[TypeInformation[ACC]]
    val resultType: TypeInformation[R]        = implicitly[TypeInformation[R]]

    asScalaStream(javaStream.aggregate(cleanedPreAggregator, applyFunction, accumulatorType, resultType))
  }

  /** Applies the given window function to each window. The window function is called for each evaluation of the window
    * for each key individually. The output of the window function is interpreted as a regular non-windowed stream.
    *
    * Arriving data is pre-aggregated using the given aggregation function.
    *
    * @param preAggregator
    *   The aggregation function that is used for pre-aggregation
    * @param windowFunction
    *   The window function.
    * @return
    *   The data stream that is the result of applying the window function to the window.
    */
  @PublicEvolving
  def aggregate[ACC: TypeInformation, V: TypeInformation, R: TypeInformation](
      preAggregator: AggregateFunction[T, ACC, V],
      windowFunction: (K, W, Iterable[V], Collector[R]) => Unit
  ): DataStream[R] = {

    val cleanedPreAggregator  = clean(preAggregator)
    val cleanedWindowFunction = clean(windowFunction)

    val applyFunction = new ScalaWindowFunction[V, R, K, W](cleanedWindowFunction)

    val accumulatorType: TypeInformation[ACC] = implicitly[TypeInformation[ACC]]
    val resultType: TypeInformation[R]        = implicitly[TypeInformation[R]]

    asScalaStream(javaStream.aggregate(cleanedPreAggregator, applyFunction, accumulatorType, resultType))
  }

  /** Applies the given window function to each window. The window function is called for each evaluation of the window
    * for each key individually. The output of the window function is interpreted as a regular non-windowed stream.
    *
    * Arriving data is pre-aggregated using the given aggregation function.
    *
    * @param preAggregator
    *   The aggregation function that is used for pre-aggregation
    * @param windowFunction
    *   The window function.
    * @return
    *   The data stream that is the result of applying the window function to the window.
    */
  @PublicEvolving
  def aggregate[ACC: TypeInformation, V: TypeInformation, R: TypeInformation](
      preAggregator: AggregateFunction[T, ACC, V],
      windowFunction: ProcessWindowFunction[V, R, K, W]
  ): DataStream[R] = {

    val cleanedPreAggregator  = clean(preAggregator)
    val cleanedWindowFunction = clean(windowFunction)

    val applyFunction = new ScalaProcessWindowFunctionWrapper[V, R, K, W](cleanedWindowFunction)

    val accumulatorType: TypeInformation[ACC]     = implicitly[TypeInformation[ACC]]
    val aggregationResultType: TypeInformation[V] = implicitly[TypeInformation[V]]
    val resultType: TypeInformation[R]            = implicitly[TypeInformation[R]]

    asScalaStream(
      javaStream.aggregate(cleanedPreAggregator, applyFunction, accumulatorType, aggregationResultType, resultType)
    )
  }

  // ---------------------------- apply() -------------------------------------

  /** Applies the given window function to each window. The window function is called for each evaluation of the window
    * for each key individually. The output of the window function is interpreted as a regular non-windowed stream.
    *
    * Note that this function requires that all data in the windows is buffered until the window is evaluated, as the
    * function provides no means of pre-aggregation.
    *
    * @param function
    *   The window function.
    * @return
    *   The data stream that is the result of applying the window function to the window.
    */
  @PublicEvolving
  def process[R: TypeInformation](function: ProcessWindowFunction[T, R, K, W]): DataStream[R] = {

    val cleanFunction = clean(function)
    val applyFunction = new ScalaProcessWindowFunctionWrapper[T, R, K, W](cleanFunction)
    asScalaStream(javaStream.process(applyFunction, implicitly[TypeInformation[R]]))
  }

  /** Applies the given window function to each window. The window function is called for each evaluation of the window
    * for each key individually. The output of the window function is interpreted as a regular non-windowed stream.
    *
    * Note that this function requires that all data in the windows is buffered until the window is evaluated, as the
    * function provides no means of pre-aggregation.
    *
    * @param function
    *   The window function.
    * @return
    *   The data stream that is the result of applying the window function to the window.
    */
  def apply[R: TypeInformation](function: WindowFunction[T, R, K, W]): DataStream[R] = {

    val cleanFunction = clean(function)
    val applyFunction = new ScalaWindowFunctionWrapper[T, R, K, W](cleanFunction)
    asScalaStream(javaStream.apply(applyFunction, implicitly[TypeInformation[R]]))
  }

  /** Applies the given window function to each window. The window function is called for each evaluation of the window
    * for each key individually. The output of the window function is interpreted as a regular non-windowed stream.
    *
    * Note that this function requires that all data in the windows is buffered until the window is evaluated, as the
    * function provides no means of pre-aggregation.
    *
    * @param function
    *   The window function.
    * @return
    *   The data stream that is the result of applying the window function to the window.
    */
  def apply[R: TypeInformation](function: (K, W, Iterable[T], Collector[R]) => Unit): DataStream[R] = {
    if (function == null) {
      throw new NullPointerException("WindowApply function must not be null.")
    }

    val cleanedFunction = clean(function)
    val applyFunction   = new ScalaWindowFunction[T, R, K, W](cleanedFunction)

    asScalaStream(javaStream.apply(applyFunction, implicitly[TypeInformation[R]]))
  }

  // ------------------------------------------------------------------------
  //  Aggregations on the keyed windows
  // ------------------------------------------------------------------------

  /** Applies an aggregation that that gives the maximum of the elements in the window at the given position.
    */
  def max(position: Int): DataStream[T] = aggregate(AggregationType.MAX, position)

  /** Applies an aggregation that that gives the maximum of the elements in the window at the given field.
    */
  def max(field: String): DataStream[T] = aggregate(AggregationType.MAX, field)

  /** Applies an aggregation that that gives the minimum of the elements in the window at the given position.
    */
  def min(position: Int): DataStream[T] = aggregate(AggregationType.MIN, position)

  /** Applies an aggregation that that gives the minimum of the elements in the window at the given field.
    */
  def min(field: String): DataStream[T] = aggregate(AggregationType.MIN, field)

  /** Applies an aggregation that sums the elements in the window at the given position.
    */
  def sum(position: Int): DataStream[T] = aggregate(AggregationType.SUM, position)

  /** Applies an aggregation that sums the elements in the window at the given field.
    */
  def sum(field: String): DataStream[T] = aggregate(AggregationType.SUM, field)

  /** Applies an aggregation that that gives the maximum element of the window by the given position. When equality,
    * returns the first.
    */
  def maxBy(position: Int): DataStream[T] = aggregate(AggregationType.MAXBY, position)

  /** Applies an aggregation that that gives the maximum element of the window by the given field. When equality,
    * returns the first.
    */
  def maxBy(field: String): DataStream[T] = aggregate(AggregationType.MAXBY, field)

  /** Applies an aggregation that that gives the minimum element of the window by the given position. When equality,
    * returns the first.
    */
  def minBy(position: Int): DataStream[T] = aggregate(AggregationType.MINBY, position)

  /** Applies an aggregation that that gives the minimum element of the window by the given field. When equality,
    * returns the first.
    */
  def minBy(field: String): DataStream[T] = aggregate(AggregationType.MINBY, field)

  private def aggregate(aggregationType: AggregationType, field: String): DataStream[T] = {
    val position = fieldNames2Indices(getInputType, Array(field))(0)
    aggregate(aggregationType, position)
  }

  def aggregate(aggregationType: AggregationType, position: Int): DataStream[T] = {

    val jStream = javaStream.asInstanceOf[JavaWStream[Product, K, W]]

    val reducer = aggregationType match {
      case AggregationType.SUM =>
        new SumAggregator(position, jStream.getInputType, jStream.getExecutionEnvironment.getConfig)

      case _ =>
        new ComparableAggregator(
          position,
          jStream.getInputType,
          aggregationType,
          true,
          jStream.getExecutionEnvironment.getConfig
        )
    }

    new DataStream[Product](jStream.reduce(reducer)).asInstanceOf[DataStream[T]]
  }

  // ------------------------------------------------------------------------
  //  Utilities
  // ------------------------------------------------------------------------

  /** Returns a "closure-cleaned" version of the given function. Cleans only if closure cleaning is not disabled in the
    * [[org.apache.flink.api.common.ExecutionConfig]].
    */
  private[flinkx] def clean[F <: AnyRef](f: F): F = {
    new StreamExecutionEnvironment(javaStream.getExecutionEnvironment).scalaClean(f)
  }

  /** Gets the output type.
    */
  private def getInputType: TypeInformation[T] = javaStream.getInputType
}
