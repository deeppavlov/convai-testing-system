package org.pavlovai

import akka.actor.{ActorRef, Props}
import info.mukel.telegrambot4s.actors.ActorBroker
import info.mukel.telegrambot4s.api.declarative.Commands
import info.mukel.telegrambot4s.api.{TelegramBot, Webhook}
import info.mukel.telegrambot4s.methods.SendMessage
import info.mukel.telegrambot4s.models.Message

import scala.util.{Failure, Try}
import scala.concurrent.Await
import scala.concurrent.duration._

object ConvaiTestingSystem extends App {
  Bot.run()
}

object Bot extends TelegramBot with Webhook with Commands {
  import info.mukel.telegrambot4s.Implicits._

  lazy val token: String = "449249979:AAGmARla8ShY7BdZLCGmX81AyDPgMXmiDAY" /*Try(system.settings.config.getString("telegram.token")).orElse {
    System.err.println("No configuration for telegram.token found!")
    Await.result(this.shutdown(), 15.seconds)
    Failure(new RuntimeException("no config"))
  }.get*/
  override val port = Option(System.getenv("PORT")).fold(80) { _.toInt }
  override val webhookUrl = "https://convaibot.herokuapp.com"

  onCommand("/begin") { implicit msg => reply("???") }
  onCommand("/end") { implicit msg => reply("!!!") }

  override def receiveMessage(msg: Message): Unit = {
    for (text <- msg.text) {
      val result = "!!!!"
      println(result)
      request(SendMessage(msg.source, result))
    }
  }

  //override def broker: Option[ActorRef] = Some(system.actorOf(Props(new HumanMessageHandler), "human-messages-handler"))
}