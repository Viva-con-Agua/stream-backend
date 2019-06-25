package daos.reader

import models.frontend.{Deposit, DepositUnit, FullAmount}
import java.util.UUID

import slick.jdbc.GetResult





/**
 * DepositUnit database reader
 *
 */

case class DepositUnitReader(
  id: Long,
  publicId: String,
  confirmed: Option[Long],
  amount: Double,
  currency: String,
  created: Long,
  depositId: Long,
  donationId: Long
  ) {
    /**
     * map database model to frontend model
     */
    def toDepositUnit(donationId: UUID) : DepositUnit = DepositUnit(UUID.fromString(this.publicId), donationId, this.confirmed, this.amount, this.currency, this.created)
  }

object DepositUnitReader extends ((Long, String, Option[Long], Double, String, Long, Long, Long) => DepositUnitReader){

//  def apply(tuple: (Long, String, Option[Long], Double, Long, Long, Long)): DepositUnitReader =
//    DepositUnitReader(tuple._1, UUID.fromString(tuple._2), tuple._3, tuple._4, tuple._5, tuple._6, tuple._7)
//
//  def unapply(arg: DepositUnitReader): Option[(Long, String, Option[Long], Double, Long, Long, Long)] =
//    Some((arg.id, arg.publicId.toString, arg.confirmed, arg.amount, arg.created, arg.depositId, arg.donationId))

  def apply(depositUnit: DepositUnit, depositId: Long, donationId: Long, id : Option[Long] = None) : DepositUnitReader =
    DepositUnitReader(
      id.getOrElse(0L),
      depositUnit.publicId.toString,
      depositUnit.confirmed,
      depositUnit.amount,
      depositUnit.currency,
      depositUnit.created,
      depositId,
      donationId
    )

  implicit val getDepositUnitReader = GetResult(r =>
    DepositUnitReader(r.nextLong, r.nextString, r.nextLongOption, r.nextDouble, r.nextString, r.nextLong, r.nextLong, r.nextLong)
  )
}



/**
 * Deposit Database representation
 */

case class DepositReader(
  id: Long,
  publicId: String,
  fullAmount: Double,
  currency: String,
  confirmed: Option[Long],
  crew: String,
  supporter: String,
  created: Long,
  updated: Long,
  dateOfDeposit: Long
  ) {
    def toDeposit(depositUnitList: List[DepositUnit]) = 
      Deposit(
        UUID.fromString(this.publicId),
        FullAmount(this.fullAmount, this.currency),
        depositUnitList,
        this.confirmed,
        UUID.fromString(this.crew),
        UUID.fromString(this.supporter),
        this.created,
        this.updated,
        this.dateOfDeposit
      )
  }

object DepositReader extends ((Long, String, Double, String, Option[Long], String, String, Long, Long, Long) => DepositReader){

//  def apply(tuple: (Long, String, Option[Long], String, String, Long, Long, Long)): DepositReader =
//    DepositReader(tuple._1, UUID.fromString(tuple._2), tuple._3, tuple._4, tuple._5, tuple._6, tuple._7, tuple._8)
//
//  def unapply(arg: DepositReader): Option[(Long, String, Option[Long], String, String, Long, Long, Long)] =
//    Some((arg.id, arg.publicId.toString, arg.confirmed, arg.crew, arg.supporter, arg.created, arg.updated, arg.dateOfDeposit))

  def apply(deposit: Deposit, id: Option[Long] = None): DepositReader =
    DepositReader(
      id.getOrElse(0L),
      deposit.publicId.toString,
      deposit.full.amount,
      deposit.full.currency,
      deposit.confirmed,
      deposit.crew.toString,
      deposit.supporter.toString,
      deposit.created,
      deposit.updated,
      deposit.dateOfDeposit
    )

  implicit val getDepositReader = GetResult(r =>
    DepositReader(r.nextLong, r.nextString, r.nextDouble, r.nextString, r.nextLongOption, r.nextString, r.nextString, r.nextLong, r.nextLong, r.nextLong)
  )
}


