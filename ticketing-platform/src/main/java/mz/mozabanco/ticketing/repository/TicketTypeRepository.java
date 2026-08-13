package mz.mozabanco.ticketing.repository;

import mz.mozabanco.ticketing.domain.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    // Lock pessimista para evitar overbooking em reservas concorrentes sobre o mesmo lote
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketType t where t.id = :id")
    Optional<TicketType> findByIdForUpdate(UUID id);
}
