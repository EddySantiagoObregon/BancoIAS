import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CollectionOrder } from '../models/payment.models';
import { toApiError } from '../utils/api-error';

@Injectable({ providedIn: 'root' })
export class CollectionOrderApiService {
  private readonly http = inject(HttpClient);

  findAll(): Observable<CollectionOrder[]> {
    return this.http
      .get<CollectionOrder[]>(`${environment.apiUrl}/collection-orders`)
      .pipe(catchError((error: HttpErrorResponse) => throwError(() => toApiError(error))));
  }
}
