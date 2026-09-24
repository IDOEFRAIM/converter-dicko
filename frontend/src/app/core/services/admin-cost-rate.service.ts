import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import {
  CostRateConfiguration,
  PublishCostRateConfigurationRequest,
} from '../models/cost-rate.model';

/**
 * Coût de revient (`/api/admin/cost-rates`), réservé aux administrateurs. Distinct de
 * {@link AdminRateService} (`/api/admin/rates`, le `RateSource` du module preferredrate) :
 * c'est CE service qui alimente le pricing des devis depuis la Phase 3.1. Aucun calcul ici —
 * le `breakEvenRate` est renvoyé par le backend.
 */
@Injectable({ providedIn: 'root' })
export class AdminCostRateService {
  private readonly baseUrl = `${environment.apiBaseUrl}/admin/cost-rates`;

  constructor(private readonly http: HttpClient) {}

  publish(
    request: PublishCostRateConfigurationRequest,
  ): Observable<ApiResponse<CostRateConfiguration>> {
    return this.http.post<ApiResponse<CostRateConfiguration>>(this.baseUrl, request);
  }

  /** Dernière configuration publiée. 404 tant qu'aucune ne l'a été. */
  current(): Observable<ApiResponse<CostRateConfiguration>> {
    return this.http.get<ApiResponse<CostRateConfiguration>>(`${this.baseUrl}/current`);
  }

  history(page = 0, size = 20): Observable<ApiResponse<PageResponse<CostRateConfiguration>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<CostRateConfiguration>>>(this.baseUrl, { params });
  }
}
