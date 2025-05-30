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
package org.apache.flinkx.api.serializer

import java.util.function.Supplier

import org.apache.flink.annotation.Internal
import org.apache.flink.api.common.typeutils.{SimpleTypeSerializerSnapshot, TypeSerializer, TypeSerializerSnapshot}
import org.apache.flink.core.memory.{DataInputView, DataOutputView}

/** Serializer for cases where no serializer is required but the system still expects one. This happens for
  * OptionTypeInfo when None is used, or for Either when one of the type parameters is Nothing.
  */
@Internal
class NothingSerializer extends ImmutableSerializer[Any] {

  override def duplicate: NothingSerializer = this

  override def createInstance: Any = {
    Integer.valueOf(-1)
  }

  override def getLength: Int = -1

  override def copy(from: Any): Any =
    throw new RuntimeException("This must not be used. You encountered a bug.")

  override def copy(from: Any, reuse: Any): Any = copy(from)

  override def serialize(any: Any, target: DataOutputView): Unit =
    throw new RuntimeException("This must not be used. You encountered a bug.")

  override def deserialize(source: DataInputView): Any =
    throw new RuntimeException("This must not be used. You encountered a bug.")

  override def snapshotConfiguration(): TypeSerializerSnapshot[Any] =
    new NothingSerializerSnapshot

  override def equals(obj: Any): Boolean = {
    obj match {
      case _: NothingSerializer => true
      case _                    => false
    }
  }

  override def hashCode(): Int = {
    classOf[NothingSerializer].hashCode()
  }
}

class NothingSerializerSnapshot
    extends SimpleTypeSerializerSnapshot[Any](
      new Supplier[TypeSerializer[Any]] {
        override def get(): TypeSerializer[Any] = new NothingSerializer
      }
    )
