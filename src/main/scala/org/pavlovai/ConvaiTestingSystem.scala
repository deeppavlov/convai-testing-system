package org.pavlovai

import akka.actor.{ActorRef, Props}
import info.mukel.telegrambot4s.actors.ActorBroker
import info.mukel.telegrambot4s.api.declarative.Commands
import info.mukel.telegrambot4s.api.{TelegramBot, Webhook}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Try}

object ConvaiTestingSystem extends App {
  Bot.run()
}

object Bot extends TelegramBot with Webhook with Commands with ActorBroker {
  lazy val token: String = Try(system.settings.config.getString("telegram.token")).orElse {
    System.err.println("No configuration for telegram.token found!")
    Await.result(this.shutdown(), 15.seconds)
    Failure(new RuntimeException("no config"))
  }.get
  override val port = Option(System.getenv("PORT")).fold(80) { _.toInt }
  override val webhookUrl = "https://convaibot.herokuapp.com"

  override def broker: Option[ActorRef] = Some(system.actorOf(Props(new HumanMessageHandler(request)), "human-messages-handler"))
}