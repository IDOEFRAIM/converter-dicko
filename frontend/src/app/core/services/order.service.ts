import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { CancelOrderRequest, CreateOrderRequest, OrderDetail, OrderSummary } from '../models/order.model';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/orders`;

  constructor(private readonly http: HttpClient) {}

  create(request: CreateOrderRequest): Observable<ApiResponse<OrderDetail>> {
    return this.http.post<ApiResponse<OrderDetail>>(this.baseUrl, request);
  }

  list(page = 0, size = 20): Observable<ApiResponse<PageResponse<OrderSummary>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<OrderSummary>>>(this.baseUrl, { params });
  }

  get(id: string): Observable<ApiResponse<OrderDetail>> {
    return this.http.get<ApiResponse<OrderDetail>>(`${this.baseUrl}/${id}`);
  }

  cancel(id: string, request: CancelOrderRequest): Observable<ApiResponse<OrderDetail>> {
    return this.http.post<ApiResponse<OrderDetail>>(`${this.baseUrl}/${id}/cancel`, request);
  }
}
