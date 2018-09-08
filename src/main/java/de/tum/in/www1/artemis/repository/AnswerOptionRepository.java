package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.AnswerOption;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the AnswerOption entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AnswerOptionRepository extends JpaRepository<AnswerOption, Long> {

}
