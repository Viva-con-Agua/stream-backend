package models.frontend

import play.api.libs.json.Json
import utils.petriNet.{Place, Token}
import daos.reader.{PlaceMessageReader}

/**
  * Represents a format that can be used for the communication between client and server.
  *
  * @author Johann Sell
  * @param name
  * @param tokens
  */
case class PlaceMessage(name: String, tokens: Int) {
  def ~ (n : String) : Boolean = name == n

  def toPlace : Place = Place(name, Token(tokens))
}

object PlaceMessage {
  def apply(p: Place) : PlaceMessage = PlaceMessage(p.name, p.token.amount)
  def apply(p: PlaceMessageReader) : PlaceMessage = PlaceMessage(p.name, p.token)

  implicit val placeMessageFormat = Json.format[PlaceMessage]
}
