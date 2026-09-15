import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { OrderDetail, OrderStatus, OrderSummary } from '../models/order.model';

@Injectable({ providedIn: 'root' })
export class AdminOrderService {
  private readonly baseUrl = `${environment.apiBaseUrl}/admin/orders`;

  constructor(private readonly http: HttpClient) {}

  list(status: OrderStatus | null, page = 0, size = 20): Observable<ApiResponse<PageResponse<OrderSummary>>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<ApiResponse<PageResponse<OrderSummary>>>(this.baseUrl, { params });
  }

  get(id: string): Observable<ApiResponse<OrderDetail>> {
    return this.http.get<ApiResponse<OrderDetail>>(`${this.baseUrl}/${id}`);
  }

  /**
   * Code QR Alipay/WeChat du beneficiaire de cet ordre (retour client : "cote admin, on doit
   * pouvoir voir les fournisseurs de chaque user, c'est ca qui permet de pouvoir faire les
   * transferts") -- 404 si ce beneficiaire n'a pas de QR (compte bancaire, ou saisie manuelle).
   */
  beneficiaryQrCode(orderId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${orderId}/beneficiary/qr-code`, { responseType: 'blob' });
  }
}
