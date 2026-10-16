import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { ApiError, PaymentReport } from '../../core/models/payment.models';
import { PaymentApiService } from '../../core/services/payment-api.service';
import { PaymentDetailComponent } from '../payment-detail/payment-detail.component';

@Component({
  selector: 'app-payment-lookup',
  standalone: true,
  imports: [ReactiveFormsModule, PaymentDetailComponent],
  templateUrl: './payment-lookup.component.html'
})
export class PaymentLookupComponent {
  private readonly paymentApi = inject(PaymentApiService);

  readonly form = inject(NonNullableFormBuilder).group({
    paymentReference: ['', [Validators.required, Validators.maxLength(50)]]
  });

  readonly loading = signal(false);
  readonly report = signal<PaymentReport | null>(null);
  readonly error = signal<ApiError | null>(null);

  search(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.report.set(null);
    this.error.set(null);

    this.paymentApi
      .findByReference(this.form.getRawValue().paymentReference.trim())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: report => this.report.set(report),
        error: (err: ApiError) => this.error.set(err)
      });
  }
}
