import { OrderStatus, PaymentStatus } from '../core/models/payment.models';

export const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  APPLIED: 'Aplicado',
  REJECTED: 'Rechazado'
};

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  OPEN: 'Habilitada',
  BLOCKED: 'Bloqueada'
};
