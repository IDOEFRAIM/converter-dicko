import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { Currency, TreasuryAccount, TreasuryAdjustmentRequest, TreasuryTransaction } from '../models/treasury.model';

@Injectable({ providedIn: 'root' })
export class AdminTreasuryService {
  private readonly baseUrl = `${environment.apiBaseUrl}/admin/treasury`;

  constructor(private readonly http: HttpClient) {}

  account(currency: Currency): Observable<ApiResponse<TreasuryAccount>> {
    return this.http.get<ApiResponse<TreasuryAccount>>(`${this.baseUrl}/accounts/${currency}`);
  }

  transactions(
    currency: Currency,
    page = 0,
    size = 30,
  ): Observable<ApiResponse<PageResponse<TreasuryTransaction>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<TreasuryTransaction>>>(
      `${this.baseUrl}/accounts/${currency}/transactions`,
      { params },
    );
  }

  deposit(request: TreasuryAdjustmentRequest): Observable<ApiResponse<TreasuryAccount>> {
    return this.http.post<ApiResponse<TreasuryAccount>>(`${this.baseUrl}/deposit`, request);
  }

  adjust(request: TreasuryAdjustmentRequest): Observable<ApiResponse<TreasuryAccount>> {
    return this.http.post<ApiResponse<TreasuryAccount>>(`${this.baseUrl}/adjust`, request);
  }
}
