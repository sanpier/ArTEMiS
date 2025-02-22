import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { JhiAlertService } from 'ng-jhipster';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { Complaint } from 'app/entities/complaint';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { Exercise, ExerciseType } from 'app/entities/exercise';

@Component({
    selector: 'jhi-complaint-form',
    templateUrl: './list-of-complaints.component.html',
    providers: [JhiAlertService],
})
export class ListOfComplaintsComponent implements OnInit {
    public complaints: Complaint[] = [];
    public hasStudentInformation = false;

    private courseId: number;
    private exerciseId: number;
    private tutorId: number;

    constructor(
        private complaintService: ComplaintService,
        private jhiAlertService: JhiAlertService,
        private route: ActivatedRoute,
        private router: Router,
        private location: Location,
    ) {}

    ngOnInit(): void {
        this.route.queryParams.subscribe(queryParams => {
            this.courseId = Number(queryParams['courseId']);
            this.exerciseId = Number(queryParams['exerciseId']);
            this.tutorId = Number(queryParams['tutorId']);
        });

        let complaintResponse: Observable<HttpResponse<Complaint[]>>;

        if (this.tutorId) {
            if (this.courseId) {
                complaintResponse = this.complaintService.findAllByTutorIdForCourseId(this.tutorId, this.courseId);
            } else {
                complaintResponse = this.complaintService.findAllByTutorIdForExerciseId(this.tutorId, this.exerciseId);
            }
        } else {
            if (this.courseId) {
                complaintResponse = this.complaintService.findAllByCourseId(this.courseId);
            } else {
                complaintResponse = this.complaintService.findAllByExerciseId(this.exerciseId);
            }
        }

        complaintResponse.subscribe(
            res => {
                this.complaints = res.body;

                if (this.complaints.length > 0 && this.complaints[0].student) {
                    this.hasStudentInformation = true;
                }
            },
            (err: HttpErrorResponse) => this.onError(err.message),
        );
    }

    openAssessmentEditor(complaint: Complaint) {
        if (!complaint || !complaint.result || !complaint.result.participation || !complaint.result.submission) {
            return;
        }

        const exercise: Exercise = complaint.result.participation.exercise;
        const submissionId = complaint.result.submission.id;

        if (!exercise || !exercise.type || !submissionId) {
            return;
        }

        const queryParams: any = {};
        let route: string;

        if (exercise.type === ExerciseType.TEXT) {
            route = `/text/${exercise.id}/assessment/${submissionId}`;
        } else if (exercise.type === ExerciseType.MODELING) {
            route = `/modeling-exercise/${exercise.id}/submissions/${submissionId}/assessment`;
            queryParams.showBackButton = true;
        }
        this.router.navigate([route], { queryParams });
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error('error.http.400', null, null);
    }

    back() {
        this.location.back();
    }
}
