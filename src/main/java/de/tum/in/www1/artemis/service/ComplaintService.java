package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.MAX_COMPLAINT_NUMBER_PER_STUDENT;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Service for managing complaints.
 */
@Service
public class ComplaintService {

    private static final String ENTITY_NAME = "complaint";

    private ComplaintRepository complaintRepository;

    private ResultRepository resultRepository;

    private ResultService resultService;

    public ComplaintService(ComplaintRepository complaintRepository, ResultRepository resultRepository, ResultService resultService) {
        this.complaintRepository = complaintRepository;
        this.resultRepository = resultRepository;
        this.resultService = resultService;
    }

    /**
     * Create a new complaint checking the user has still enough complaint to create
     *
     * @param complaint the complaint to create
     * @return the saved complaint
     */
    @Transactional
    public Complaint createComplaint(Complaint complaint, Principal principal) {
        Result originalResult = resultRepository.findById(complaint.getResult().getId())
                .orElseThrow(() -> new BadRequestAlertException("The result you are referring to does not exist", ENTITY_NAME, "resultnotfound"));
        User originalSubmissor = originalResult.getParticipation().getStudent();
        Long courseId = originalResult.getParticipation().getExercise().getCourse().getId();

        long numberOfUnacceptedComplaints = countUnacceptedComplaintsByStudentIdAndCourseId(originalSubmissor.getId(), courseId);
        if (numberOfUnacceptedComplaints >= MAX_COMPLAINT_NUMBER_PER_STUDENT) {
            throw new BadRequestAlertException("You cannot have more than " + MAX_COMPLAINT_NUMBER_PER_STUDENT + " open or rejected complaints at the same time.", ENTITY_NAME,
                    "toomanycomplaints");
        }
        if (originalResult.getCompletionDate().isBefore(ZonedDateTime.now().minusWeeks(1))) {
            throw new BadRequestAlertException("You cannot submit a complaint for a result that is older than one week.", ENTITY_NAME, "resultolderthanaweek");
        }
        if (!originalSubmissor.getLogin().equals(principal.getName())) {
            throw new BadRequestAlertException("You can create a complaint only for a result you submitted", ENTITY_NAME, "differentuser");
        }

        originalResult.setHasComplaint(true);

        complaint.setSubmittedTime(ZonedDateTime.now());
        complaint.setStudent(originalSubmissor);
        complaint.setResult(originalResult);
        try {
            // Store the original result with the complaint
            complaint.setResultBeforeComplaint(resultService.getOriginalResultAsString(originalResult));
        }
        catch (JsonProcessingException exception) {
            throw new InternalServerErrorException("Failed to store original result");
        }

        resultRepository.save(originalResult);

        return complaintRepository.save(complaint);
    }

    @Transactional(readOnly = true)
    public Optional<Complaint> getById(long complaintId) {
        return complaintRepository.findById(complaintId);
    }

    @Transactional(readOnly = true)
    public Optional<Complaint> getByResultId(long resultId) {
        return complaintRepository.findByResult_Id(resultId);
    }

    @Transactional(readOnly = true)
    public long countUnacceptedComplaintsByStudentIdAndCourseId(long studentId, long courseId) {
        return complaintRepository.countUnacceptedComplaintsByStudentIdAndCourseId(studentId, courseId);
    }

    @Transactional(readOnly = true)
    public long countComplaintsByCourseId(long courseId) {
        return complaintRepository.countByResult_Participation_Exercise_Course_Id(courseId);
    }

    @Transactional(readOnly = true)
    public long countComplaintsByExerciseId(long exerciseId) {
        return complaintRepository.countByResult_Participation_Exercise_Id(exerciseId);
    }

    /**
     * Given an exercise id, retrieve all the complaints apart the ones related to whoever is calling the method. Useful for creating a list of complaints a tutor can review.
     *
     * @param exerciseId - the id of the exercise we are interested in
     * @param principal  - the callee
     * @return a list of complaints
     */
    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaintsByExerciseIdButMine(long exerciseId, Principal principal) {
        List<Complaint> responseComplaints = new ArrayList<>();

        Optional<List<Complaint>> databaseComplaints = complaintRepository.findByResult_Participation_Exercise_IdWithEagerSubmissionAndEagerAssessor(exerciseId);

        if (!databaseComplaints.isPresent()) {
            return responseComplaints;
        }

        databaseComplaints.get().forEach(complaint -> {
            String submissorName = principal.getName();
            User assessor = complaint.getResult().getAssessor();

            if (!assessor.getLogin().equals(submissorName)) {
                // Remove data about the student
                complaint.getResult().getParticipation().setStudent(null);
                complaint.setStudent(null);
                complaint.setResultBeforeComplaint(null);

                responseComplaints.add(complaint);
            }
        });

        return responseComplaints;
    }

    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaintsByTutorId(Long tutorId) {
        List<Complaint> complaints = complaintRepository.getAllByResult_Assessor_Id(tutorId);

        return filterOutStudentFromComplaints(complaints);
    }

    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaintsByCourseId(Long courseId, boolean includeStudentsName) {
        List<Complaint> complaints = complaintRepository.getAllByResult_Participation_Exercise_Course_Id(courseId);

        return includeStudentsName ? complaints : filterOutStudentFromComplaints(complaints);
    }

    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaintsByCourseIdAndTutorId(Long courseId, Long tutorId, boolean includeStudentsName) {
        List<Complaint> complaints = complaintRepository.getAllByResult_Assessor_IdAndResult_Participation_Exercise_Course_Id(tutorId, courseId);

        return includeStudentsName ? complaints : filterOutStudentFromComplaints(complaints);
    }

    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaintsByExerciseId(Long exerciseId, boolean includeStudentsName) {
        List<Complaint> complaints = complaintRepository.getAllByResult_Participation_Exercise_Id(exerciseId);

        return includeStudentsName ? complaints : filterOutStudentFromComplaints(complaints);
    }

    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaintsByExerciseIdAndTutorId(Long exerciseId, Long tutorId, boolean includeStudentsName) {
        List<Complaint> complaints = complaintRepository.getAllByResult_Assessor_IdAndResult_Participation_Exercise_Id(tutorId, exerciseId);

        return includeStudentsName ? complaints : filterOutStudentFromComplaints(complaints);
    }

    private void filterOutStudentFromComplaint(Complaint complaint) {
        complaint.setStudent(null);
        complaint.setResultBeforeComplaint(null);

        if (complaint.getResult() != null && complaint.getResult().getParticipation() != null) {
            complaint.getResult().getParticipation().setStudent(null);
        }
    }

    private List<Complaint> filterOutStudentFromComplaints(List<Complaint> complaints) {
        complaints.forEach(this::filterOutStudentFromComplaint);

        return complaints;
    }
}
