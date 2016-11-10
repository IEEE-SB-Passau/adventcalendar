package akka

import akka.actor.{Cell, ActorRefWithCell, ActorRef}

/**
  * Hack to make akka's internal API accessible, so we can get some info
  */
trait AkkaScopingHelper {

  /**
    * Gets the underlying Cell of an ActorRef
    *
    * @param ref the ActorRef
    * @return the Cell object of the actor
    */
  def getCell(ref: ActorRef): Cell = ref.asInstanceOf[ActorRefWithCell].underlying
}
