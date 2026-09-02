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
}
