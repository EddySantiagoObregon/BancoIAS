import { Component, inject, signal } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, finalize, of } from 'rxjs';
import { ApiError, PaymentSubmission } from '../../core/models/payment.models';
import { PaymentApiService } from '../../core/services/payment-api.service';
import { CollectionOrderApiService } from '../../core/services/collection-order-api.service';
import { PaymentEventsService } from '../../core/services/payment-events.service';
import { PaymentDetailComponent } from '../payment-detail/payment-detail.component';

const REFERENCE_PATTERN = /^[A-Za-z0-9_-]+$/;

@Component({
  selector: 'app-payment-form',
  standalone: true,
  imports: [ReactiveFormsModule, AsyncPipe, PaymentDetailComponent],
  templateUrl: './payment-form.component.html'
})
export class PaymentFormComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly paymentApi = inject(PaymentApiService);
  private readonly events = inject(PaymentEventsService);

  // Solo sugerencias para el campo orden; se puede escribir cualquier valor para probar rechazos.
  readonly orderOptions$ = inject(CollectionOrderApiService).findAll().pipe(catchError(() => of([])));

  readonly form = this.fb.group({
    paymentReference: ['', [Validators.required, Validators.maxLength(50), Validators.pattern(REFERENCE_PATTERN)]],
    collectionOrderId: ['ORD-PSE-1001', [Validators.required, Validators.maxLength(30)]],
    merchantId: ['COM-10', [Validators.required, Validators.maxLength(30)]],
    amount: this.fb.control<number | null>(null, Validators.required)
  });

  readonly submitting = signal(false);
  readonly submission = signal<PaymentSubmission | null>(null);
  readonly error = signal<ApiError | null>(null);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.submitting.set(true);
    this.submission.set(null);
    this.error.set(null);

    this.paymentApi
      .register({
        paymentReference: value.paymentReference.trim(),
        collectionOrderId: value.collectionOrderId.trim(),
        merchantId: value.merchantId.trim(),
        amount: Number(value.amount)
      })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: submission => {
          this.submission.set(submission);
          this.events.notifyProcessed(submission.report);
        },
        error: (err: ApiError) => this.error.set(err)
      });
  }

  clear(): void {
    this.form.reset();
    this.submission.set(null);
    this.error.set(null);
  }

  generateReference(): void {
    const stamp = new Date().toISOString().replace(/\D/g, '').slice(2, 14);
    this.form.controls.paymentReference.setValue(`PSE-${stamp}`);
  }

  hasError(control: keyof typeof this.form.controls, error: string): boolean {
    const c = this.form.controls[control];
    return c.touched && c.hasError(error);
  }
}
