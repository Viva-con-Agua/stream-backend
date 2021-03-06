package services

import java.util.UUID

import daos.{HouseholdDAO, UserDAO}
import javax.inject.Inject
import models.frontend._
import utils.{HouseholdFilter, Page, Sort}

import scala.concurrent.{ExecutionContext, Future}

class HouseholdService @Inject() (dao: HouseholdDAO, userDAO: UserDAO) {

  implicit val ec = ExecutionContext.global

  private def prepare(filter: Option[HouseholdFilter]): Future[Option[HouseholdFilter]] =
    this.dao.all(None, None, None).flatMap(households => {
      // get ids of all referenced users inside the database!
      val userIds = households.filter(_.versions.head.author.isDefined).map(_.versions.head.author.get).distinct
      // get all user (UUIDs) of the same crew as the given one by the filter
      filter.map(hf => hf.crew match {
        case Some(id) => userDAO.crewSupporter(userIds, id).map(supporter =>
          hf >> supporter
        )
        case _ => Future.successful(hf)
      }) match {
        // Option[Future[...]] to Future[Option[...]]
        case Some(f) => f.map(Some(_))
        case None => Future.successful(None)
      }})

  def count(filter: Option[HouseholdFilter]) : Future[Int] =
    dao.count(filter)
//    this.prepare(filter).flatMap(hf => dao.count(hf))

  def all(page: Page, sort: Sort, filter: Option[HouseholdFilter]) : Future[List[Household]] =
    dao.all(Some(page), Some(sort), filter)
//    this.prepare(filter).flatMap(hf => dao.all(Some(page), Some(sort), hf))

  def save(household: Household, user: UUID): Future[Option[Household]] = {
    dao.save(household.setAuthor(user)).flatMap(_ match {
      case Some(household) => household.versions.headOption match {
        case Some(version) => version.author match {
          case Some(author) => userDAO.updateDatasource(author).map(_ match {
            case true => Some(household) // successful update
            case false => Some(household) // unsuccessful update; roll-back only because potentially missing network connection?!
          })
          case None => Future.successful(Some(household)) // not possible to sort by authors crew, because of missing author
        }
        case None => Future.successful(Some(household)) // not possible to sort by authors crew, because of missing version
      }
      case None => Future.successful(None) // given household has not been saved
    })
  }
  /**
    * Updates a given household entry considering the editor and if the state has to change based on the given version.
    *
    * @author Johann Sell
    * @param household
    * @param user
    * @return
    */
  def update(household: Household, user: UUID): Future[Option[Household]] = {
    val switched = checkForSwitch(household, user)
    dao.update(complete(switched))
  }

  /**
    * Checks if the household entry switches from `REQUESTED` to `APPLIEDFOR` or the other way around by the last version
    * and updates the Petri Net, if required.
    *
    * @author Johann Sell
    * @param household
    * @param user
    * @return
    */
  private def checkForSwitch(household: Household, user: UUID) : Household = household.versions.lastOption match {
    case Some(version) => version.isRequest match {
      case true => household.state ? PlaceMessage("AppliedFor", 1) match {
        case true => household.state match { // current version is marked as "Requested", but the state is "AppliedFor" - SWITCH!
          case petriNet: PetriNetHouseholdState => ActionMessageExecuter.from("request", petriNet).fold(
            error => household.update(user),
            action => household.state.transform(action.msg) match {
              case Right(newState) => household.updateStateByEditor(newState, user, "editor")
              case Left(error) => household.update(user)
            }
          )
          case _ => household.update(user) // Not a petri net based state. If there will be something implemented, consider it here!
        }
        case false => household.update(user) // There is nothing to do, it has already the correct state
      }
      case false => household.state ? PlaceMessage("Requested", 1) match {
        case true => household.state match { // current version is marked as "AppliedFor", but the state is "Requested" - SWITCH!
          case petriNet: PetriNetHouseholdState => ActionMessageExecuter.from("apply", petriNet).fold(
            error => household.update(user),
            action => household.state.transform(action.msg) match {
              case Right(newState) => household.updateStateByEditor(newState, user, "editor")
              case Left(error) => household.update(user)
            }
          )
          case _ => household.update(user) // Not a petri net based state. If there will be something implemented, consider it here!
        }
        case false => household.update(user) // There is nothing to do, it has already the correct state
      }
    }
    case None => household.update(user)
  }

  /**
    * Checks if the household has been completed by the last version (or is now incomplete, after it has been complete
    * before). It updates the Petri Net, if required.
    *
    * @author Johann Sell
    * @param household
    * @return
    */
  private def complete(household: Household): Household = household.versions.lastOption match {
    case Some(version) => version.isComplete match {
      case true => household.state.transform(ActionMessage("complete")).fold(
        notAllowed => household,
        updated => Household(household.id, updated, household.versions)
      )
      case false => household.state ? PlaceMessage("HouseholdComplete", 1) match {
        case true => household.state.transform(ActionMessage("incomplete")).fold(
          notAllowed => household,
          updated => Household(household.id, updated, household.versions)
        )
        case false => household
      }
    }
    case None => household
  }

  /**
    * Updates a households state by a given action
    *
    * @author Johann Sell
    * @param action
    * @param uuid
    * @return
    */
  def stateUpdate(actions: List[ActionMessage], uuid: UUID, user: UUID, role: String): Future[Either[Exception, Household]] = {
    def execute(household: Household, actions: List[ActionMessage]) : Future[Either[Exception, Household]] =
      actions.headOption match {
        case Some(action) => household.state.transform(action) match {
          case Right(newState) => dao.update(household.setNewState(newState, user, role)).map(_.map(Right( _ )).getOrElse(Left(new NotFoundAfterUpdate)))
          case Left(error) => error match {
            case e : TransformationNotAllowed => execute(household, actions.tail)
            case error => Future.successful(Left(error))
          }
        }
        case None => Future.successful(Left(new NoActionAlternativeHasBeenExecuted))
      }

    dao.find(uuid).flatMap(_ match {
      case Some(household) => execute(household, actions)
      case None => Future.successful(Left(new NoHouseholdEntry))
    })
  }

  class NoActionAlternativeHasBeenExecuted extends Exception("Their is no valid and executable action given.")
  class NotFoundAfterUpdate extends Exception("Household has not been found inside the database, after the update operation has been executed.")
  class NoHouseholdEntry extends Exception("The household entry has not been found inside the database.")
}
