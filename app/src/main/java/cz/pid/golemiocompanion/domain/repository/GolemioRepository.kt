package cz.pid.golemiocompanion.domain.repository

import cz.pid.golemiocompanion.domain.model.DepartureInfo
import cz.pid.golemiocompanion.domain.model.LiveVehiclePosition
import cz.pid.golemiocompanion.domain.model.ParkingLotInfo
import cz.pid.golemiocompanion.domain.model.PidStop
import cz.pid.golemiocompanion.domain.model.SharedBikeInfo

interface GolemioRepository {
    suspend fun getDeparturesForStop(stop: PidStop): Result<List<DepartureInfo>>
    suspend fun getLiveVehicles(routeFilter: String? = null): Result<List<LiveVehiclePosition>>
    suspend fun getParkingLots(): Result<List<ParkingLotInfo>>
    suspend fun getSharedBikes(): Result<List<SharedBikeInfo>>
}