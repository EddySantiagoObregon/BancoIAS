import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { PaymentReport } from '../models/payment.models';

/**
 * Avisa a los paneles (historial, saldos) que se procesó un reporte para que recarguen desde el backend.
 */
@Injectable({ providedIn: 'root' })
export class PaymentEventsService {
  private readonly processed = new Subject<PaymentReport>();
  readonly processed$ = this.processed.asObservable();

  notifyProcessed(report: PaymentReport): void {
    this.processed.next(report);
  }
}
