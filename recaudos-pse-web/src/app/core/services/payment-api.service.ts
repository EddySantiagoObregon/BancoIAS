import { HttpClient, HttpErrorResponse, HttpParams, HttpStatusCode } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PaymentReport, PaymentReportRequest, PaymentSubmission } from '../models/payment.models';
import { toApiError } from '../utils/api-error';

@Injectable({ providedIn: 'root' })
export class PaymentApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/payments`;

  /** 201 = reporte nuevo, 200 = referencia repetida con los mismos datos (resultado original). */
  register(request: PaymentReportRequest): Observable<PaymentSubmission> {
    return this.http.post<PaymentReport>(this.baseUrl, request, { observe: 'response' }).pipe(
      map(response => ({
        report: response.body as PaymentReport,
        replayed: response.status === HttpStatusCode.Ok
      })),
      catchError(this.handleError)
    );
  }

  findByReference(paymentReference: string): Observable<PaymentReport> {
    return this.http
      .get<PaymentReport>(`${this.baseUrl}/${encodeURIComponent(paymentReference)}`)
      .pipe(catchError(this.handleError));
  }

  findRecent(limit = 20, collectionOrderId?: string): Observable<PaymentReport[]> {
    let params = new HttpParams().set('limit', limit);
    if (collectionOrderId) {
      params = params.set('collectionOrderId', collectionOrderId);
    }
    return this.http.get<PaymentReport[]>(this.baseUrl, { params }).pipe(catchError(this.handleError));
  }

  private handleError(error: HttpErrorResponse) {
    return throwError(() => toApiError(error));
  }
}
