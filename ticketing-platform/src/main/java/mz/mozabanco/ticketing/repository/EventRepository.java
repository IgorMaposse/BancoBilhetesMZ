package mz.mozabanco.ticketing.repository;

import mz.mozabanco.ticketing.domain.Event;
import mz.mozabanco.ticketing.domain.enums.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findByStatus(EventStatus status);
    List<Event> findByOrganizerId(UUID organizerId);
    List<Event> findByStatusAndCategoryIgnoreCase(EventStatus status, String category);
}
