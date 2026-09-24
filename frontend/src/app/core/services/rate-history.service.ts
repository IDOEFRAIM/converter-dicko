import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import {
  CreateRateAlertRequest,
  PublicRateHistoryEntry,
  RateAlert,
  RateAlertStatus,
  RateHistoryQuery,
} from '../models/rate-history.model';

/**
 * Historique public du taux client + alertes de taux. Le frontend affiche exactement ce que
 * le backend renvoie : jamais de taux de revient, de marge ni de frais internes, aucun calcul
 * de declenchement cote client (le scheduler backend en est seul responsable).
 */
@Injectable({ providedIn: 'root' })
export class RateHistoryService {
  private readonly ratesUrl = `${environment.apiBaseUrl}/v1/rates`;
  private readonly alertsUrl = `${environment.apiBaseUrl}/v1/rate-alerts`;

  constructor(private readonly http: HttpClient) {}

  history(query: RateHistoryQuery = {}): Observable<ApiResponse<PageResponse<PublicRateHistoryEntry>>> {
    let params = new HttpParams().set('page', query.page ?? 0).set('size', query.size ?? 30);
    if (query.pair) {
      params = params.set('pair', query.pair);
    }
    if (query.from) {
      params = params.set('from', query.from);
    }
    if (query.to) {
      params = params.set('to', query.to);
    }
    return this.http.get<ApiResponse<PageResponse<PublicRateHistoryEntry>>>(`${this.ratesUrl}/history`, {
      params,
    });
  }

  listAlerts(
    page = 0,
    size = 20,
    status?: RateAlertStatus,
  ): Observable<ApiResponse<PageResponse<RateAlert>>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<ApiResponse<PageResponse<RateAlert>>>(this.alertsUrl, { params });
  }

  createAlert(request: CreateRateAlertRequest): Observable<ApiResponse<RateAlert>> {
    return this.http.post<ApiResponse<RateAlert>>(this.alertsUrl, request);
  }

  cancelAlert(id: string): Observable<ApiResponse<RateAlert>> {
    return this.http.post<ApiResponse<RateAlert>>(`${this.alertsUrl}/${id}/cancel`, {});
  }
}
