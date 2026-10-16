export type PaymentStatus = 'APPLIED' | 'REJECTED';
export type OrderStatus = 'OPEN' | 'BLOCKED';

export interface PaymentReportRequest {
  paymentReference: string;
  collectionOrderId: string;
  merchantId: string;
  amount: number;
}

export interface PaymentReport {
  paymentReference: string;
  collectionOrderId: string;
  merchantId: string;
  amount: number;
  status: PaymentStatus;
  reasonCode: string;
  reasonMessage: string;
  balanceAfter?: number;
  processedAt: string;
}

/** Resultado de un POST: `replayed` indica que la referencia ya existía y el core devolvió el original. */
export interface PaymentSubmission {
  report: PaymentReport;
  replayed: boolean;
}

export interface CollectionOrder {
  id: string;
  merchantId: string;
  status: OrderStatus;
  pendingBalance: number;
  updatedAt: string;
}

export interface FieldErrorDetail {
  field: string;
  message: string;
}

export interface ApiError {
  status: number;
  code: string;
  message: string;
  details: FieldErrorDetail[];
}
