import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../models/payment.models';

export function toApiError(error: HttpErrorResponse): ApiError {
  if (error.status === 0) {
    return {
      status: 0,
      code: 'BACKEND_UNAVAILABLE',
      message: 'No fue posible comunicarse con el core de recaudos. Verifique que el backend esté en ejecución.',
      details: []
    };
  }
  const body = error.error;
  if (body && typeof body === 'object' && 'code' in body) {
    return {
      status: error.status,
      code: body.code,
      message: body.message ?? 'Error procesando la solicitud',
      details: body.details ?? []
    };
  }
  return {
    status: error.status,
    code: 'HTTP_' + error.status,
    message: 'Respuesta inesperada del servidor (' + error.status + ')',
    details: []
  };
}
