/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.spray

import akka.pattern.{AskTimeoutException, PromiseActorRef}
import akka.dispatch.Promise
import akka.util.Duration
import akka.actor.{InternalActorRef, ActorRef, ActorRefProvider}

object AskSupport {

  def createAsker(provider: ActorRefProvider, timeout: Duration): PromiseActorRef = {
    if (timeout <= Duration.Zero) throw new IllegalArgumentException("timeout must be > 0")
    val path = provider.tempPath()
    val result = Promise[Any]()(provider.dispatcher)
    val a = new PromiseActorRef(provider, path, provider.tempContainer, result, provider.deathWatch)
    provider.registerTempActor(a, path)
    def unregister() {
      try a.stop() finally provider.unregisterTempActor(path)
    }
    if (timeout.isFinite) {
      val f = provider.scheduler.scheduleOnce(timeout) {
        result.tryComplete(Left(new AskTimeoutException("Timed out")))
      }
      result onComplete { _ => try unregister() finally f.cancel() }
    } else result onComplete { _ => unregister() }
    a
  }

}

abstract class MinimalActorRef(related: ActorRef) extends akka.actor.MinimalActorRef {
  lazy val path = provider.tempPath()
  def provider = related.asInstanceOf[InternalActorRef].provider
}