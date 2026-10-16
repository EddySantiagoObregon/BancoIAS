import { Component } from '@angular/core';
import { DatePipe } from '@angular/common';
import { PaymentFormComponent } from './features/payment-form/payment-form.component';
import { PaymentLookupComponent } from './features/payment-lookup/payment-lookup.component';
import { RecentPaymentsComponent } from './features/recent-payments/recent-payments.component';
import { OrdersPanelComponent } from './features/orders-panel/orders-panel.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [DatePipe, PaymentFormComponent, PaymentLookupComponent, RecentPaymentsComponent, OrdersPanelComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  readonly today = new Date();
}
