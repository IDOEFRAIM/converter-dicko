import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { Wallet, WalletTransaction } from '../models/wallet.model';

@Injectable({ providedIn: 'root' })
export class WalletService {
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/wallet`;

  constructor(private readonly http: HttpClient) {}

  get(): Observable<ApiResponse<Wallet>> {
    return this.http.get<ApiResponse<Wallet>>(this.baseUrl);
  }

  transactions(page = 0, size = 20): Observable<ApiResponse<PageResponse<WalletTransaction>>> {
    return this.http.get<ApiResponse<PageResponse<WalletTransaction>>>(`${this.baseUrl}/transactions`, {
      params: { page, size },
    });
  }
}
