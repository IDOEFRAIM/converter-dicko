import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { AdminUserDetail, AdminUserSummary, BlockUserRequest, UserAccountStatus } from '../models/admin-user.model';
import { SupplierDetail } from '../models/supplier.model';

/**
 * Acces a `/api/admin/users/**` — retour client sept. 2026 : "l'admin n'arrive pas a voir les
 * detail des different fournisseur pour chaque utilisateur...c'est ca qui permet de pouvoir faire
 * les transfert". `suppliers`/`supplierQrCode` completent la vue deja existante d'un ordre precis
 * (voir `AdminOrderService.beneficiaryQrCode`) en donnant acces au carnet COMPLET d'un client,
 * meme avant qu'il n'ait cree un ordre.
 */
@Injectable({ providedIn: 'root' })
export class AdminUserService {
  private readonly baseUrl = `${environment.apiBaseUrl}/admin/users`;

  constructor(private readonly http: HttpClient) {}

  list(
    status: UserAccountStatus | null,
    search: string | null,
    page = 0,
    size = 20,
  ): Observable<ApiResponse<PageResponse<AdminUserSummary>>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    if (search) {
      params = params.set('search', search);
    }
    return this.http.get<ApiResponse<PageResponse<AdminUserSummary>>>(this.baseUrl, { params });
  }

  get(id: string): Observable<ApiResponse<AdminUserDetail>> {
    return this.http.get<ApiResponse<AdminUserDetail>>(`${this.baseUrl}/${id}`);
  }

  block(id: string, request: BlockUserRequest): Observable<ApiResponse<AdminUserDetail>> {
    return this.http.post<ApiResponse<AdminUserDetail>>(`${this.baseUrl}/${id}/block`, request);
  }

  unblock(id: string): Observable<ApiResponse<AdminUserDetail>> {
    return this.http.post<ApiResponse<AdminUserDetail>>(`${this.baseUrl}/${id}/unblock`, {});
  }

  verifyKyc(id: string): Observable<ApiResponse<AdminUserDetail>> {
    return this.http.post<ApiResponse<AdminUserDetail>>(`${this.baseUrl}/${id}/kyc/verify`, {});
  }

  revokeKyc(id: string): Observable<ApiResponse<AdminUserDetail>> {
    return this.http.post<ApiResponse<AdminUserDetail>>(`${this.baseUrl}/${id}/kyc/revoke`, {});
  }

  /** Carnet complet de ce client — detail non masque (numero de compte en clair). */
  suppliers(id: string, page = 0, size = 50): Observable<ApiResponse<PageResponse<SupplierDetail>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<SupplierDetail>>>(`${this.baseUrl}/${id}/suppliers`, { params });
  }

  /** 404 si ce fournisseur n'a pas de code QR (compte bancaire, ou pas encore televerse). */
  supplierQrCode(userId: string, supplierId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${userId}/suppliers/${supplierId}/qr-code`, { responseType: 'blob' });
  }
}
