package com.railscale.train.service;

import com.railscale.common.exception.ConflictException;
import com.railscale.common.exception.ResourceNotFoundException;
import com.railscale.train.domain.BerthType;
import com.railscale.train.domain.Coach;
import com.railscale.train.domain.CoachType;
import com.railscale.train.domain.Seat;
import com.railscale.train.domain.Station;
import com.railscale.train.domain.Train;
import com.railscale.train.repository.CoachRepository;
import com.railscale.train.repository.SeatRepository;
import com.railscale.train.repository.StationRepository;
import com.railscale.train.repository.TrainRepository;
import com.railscale.train.web.dto.CreateStationRequest;
import com.railscale.train.web.dto.CreateTrainRequest;
import com.railscale.train.web.dto.StationResponse;
import com.railscale.train.web.dto.TrainResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TrainAdminService {

    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final CoachRepository coachRepository;
    private final SeatRepository seatRepository;

    public TrainAdminService(
        StationRepository stationRepository,
        TrainRepository trainRepository,
        CoachRepository coachRepository,
        SeatRepository seatRepository
    ) {
        this.stationRepository = stationRepository;
        this.trainRepository = trainRepository;
        this.coachRepository = coachRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public StationResponse createStation(CreateStationRequest request) {
        String code = request.code().toUpperCase().trim();
        if (stationRepository.existsByCode(code)) {
            throw new ConflictException("Station with code " + code + " already exists");
        }

        Station station = new Station(code, request.name().trim(), request.city().trim());
        Station saved = stationRepository.save(station);
        return StationResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<StationResponse> getAllStations() {
        return stationRepository.findAll().stream()
            .map(StationResponse::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public Station getStationEntity(UUID id) {
        return stationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Station not found with id: " + id));
    }

    @Transactional
    public TrainResponse createTrain(CreateTrainRequest request) {
        String trainNumber = request.trainNumber().toUpperCase().trim();
        if (trainRepository.existsByTrainNumber(trainNumber)) {
            throw new ConflictException("Train with number " + trainNumber + " already exists");
        }

        Train train = new Train(trainNumber, request.name().trim());

        if (request.coaches() != null) {
            for (CreateTrainRequest.CreateCoachRequest coachReq : request.coaches()) {
                Coach coach = new Coach(train, coachReq.coachNumber().toUpperCase().trim(), coachReq.coachType());
                
                // Generate seats for the coach
                for (int i = 1; i <= coachReq.totalSeats(); i++) {
                    BerthType berthType = assignBerthType(coachReq.coachType(), i);
                    Seat seat = new Seat(coach, String.valueOf(i), berthType);
                    coach.addSeat(seat);
                }

                train.addCoach(coach);
            }
        }

        Train saved = trainRepository.save(train);
        return TrainResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public TrainResponse getTrain(UUID id) {
        Train train = trainRepository.findWithCoachesAndSeatsById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Train not found with id: " + id));
        return TrainResponse.fromEntity(train);
    }

    @Transactional(readOnly = true)
    public List<TrainResponse> getAllTrains() {
        return trainRepository.findAll().stream()
            .map(TrainResponse::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public Train getTrainEntity(UUID id) {
        return trainRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Train not found with id: " + id));
    }

    private BerthType assignBerthType(CoachType coachType, int seatNumber) {
        if (coachType == CoachType.CHAIR_CAR) {
            return (seatNumber % 3 == 0) ? BerthType.WINDOW : BerthType.AISLE;
        }

        int remainder = seatNumber % 8;
        return switch (remainder) {
            case 1, 4 -> BerthType.LOWER;
            case 2, 5 -> BerthType.MIDDLE;
            case 3, 6 -> BerthType.UPPER;
            case 7 -> BerthType.SIDE_LOWER;
            case 0 -> BerthType.SIDE_UPPER;
            default -> BerthType.LOWER;
        };
    }
}
