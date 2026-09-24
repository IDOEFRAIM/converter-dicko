import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { RateAlertsPage } from './rate-alerts.page';
import { RateAlert } from '../../../core/models/rate-history.model';

function alertsPage(content: RateAlert[]) {
  return { data: { content, page: 0, size: 50, totalElements: content.length, totalPages: 1, first: true, last: true }, message: 'ok' };
}

function ratePage(customerRate: string) {
  return {
    data: {
      content: [{ currencyPair: 'XOF/CNY', customerRate, recordedAt: '2026-02-01T10:00:00Z' }],
      page: 0,
      size: 1,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    },
    message: 'ok',
  };
}

function alert(overrides: Partial<RateAlert> = {}): RateAlert {
  return {
    id: 'a1',
    currencyPair: 'XOF/CNY',
    direction: 'XOF_TO_CNY',
    targetRate: '86.00',
    comparison: 'LESS_THAN_OR_EQUAL',
    status: 'ACTIVE',
    createdAt: '2026-01-01T10:00:00Z',
    expiresAt: null,
    triggeredAt: null,
    cancelledAt: null,
    expiredAt: null,
    ...overrides,
  };
}

describe('RateAlertsPage', () => {
  let fixture: ComponentFixture<RateAlertsPage>;
  let httpMock: HttpTestingController;

  async function createWith(alerts: RateAlert[], customerRate = '87.01', dialogConfirmValue: boolean | null = null) {
    await TestBed.configureTestingModule({
      imports: [RateAlertsPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(dialogConfirmValue) }) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RateAlertsPage);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/v1/rate-alerts' && r.method === 'GET').flush(alertsPage(alerts));
    httpMock.expectOne((r) => r.url === '/api/v1/rates/history').flush(ratePage(customerRate));
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('separates active alerts from completed ones', async () => {
    await createWith([alert({ id: 'a1', status: 'ACTIVE' }), alert({ id: 'a2', status: 'TRIGGERED', triggeredAt: '2026-02-01T10:00:00Z' })]);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Alertes actives');
    expect(text).toContain('Alertes terminees');
  });

  it('shows the real current rate and a computed gap for an active alert, never an invented figure', async () => {
    await createWith([alert({ id: 'a1', status: 'ACTIVE', targetRate: '86.00' })], '87.01');
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('87.01');
    expect(fixture.componentInstance.gapFor(alert({ targetRate: '86.00' }))!.percent).toBeCloseTo(
      ((86 - 87.01) / 87.01) * 100,
      5,
    );
  });

  it('shows a non-transactional "objectif atteint" narrative for a TRIGGERED alert, never claiming a purchase happened', async () => {
    await createWith([alert({ id: 'a1', status: 'TRIGGERED', triggeredAt: '2026-02-01T10:00:00Z', targetRate: '86.00' })]);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Objectif atteint');
    expect(text.toLowerCase()).not.toContain('achat');
    expect(text.toLowerCase()).not.toContain('transaction');
  });

  it('shows the expired narrative only for an EXPIRED alert', async () => {
    await createWith([alert({ id: 'a1', status: 'EXPIRED', expiresAt: '2026-02-01T00:00:00Z' })]);
    expect(fixture.nativeElement.textContent).toContain("n'a pas ete atteint");
  });

  it('creates an alert from the form and reloads the list', async () => {
    await createWith([]);
    fixture.componentInstance.form.patchValue({ targetRate: 86 });
    fixture.componentInstance.create();

    const req = httpMock.expectOne('/api/v1/rate-alerts');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ targetRate: '86', expiresAt: null });
    req.flush({ data: alert({ targetRate: '86.00' }), message: 'ok' });

    httpMock.expectOne((r) => r.url === '/api/v1/rate-alerts' && r.method === 'GET').flush(alertsPage([alert({ targetRate: '86.00' })]));
    httpMock.expectOne((r) => r.url === '/api/v1/rates/history').flush(ratePage('87.01'));
  });

  it('rejects a non-positive target rate before submitting', async () => {
    await createWith([]);
    fixture.componentInstance.form.patchValue({ targetRate: 0 });
    fixture.componentInstance.create();
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('never calls the cancel API when the confirmation dialog is dismissed', async () => {
    await createWith([alert({ id: 'a1', status: 'ACTIVE' })], '87.01', null);
    fixture.componentInstance.cancel(alert({ id: 'a1', status: 'ACTIVE' }));
    httpMock.expectNone('/api/v1/rate-alerts/a1/cancel');
  });

  it('cancels an active alert once confirmed, updating it in place from the response', async () => {
    await createWith([alert({ id: 'a1', status: 'ACTIVE' })], '87.01', true);
    fixture.componentInstance.cancel(alert({ id: 'a1', status: 'ACTIVE' }));

    const req = httpMock.expectOne('/api/v1/rate-alerts/a1/cancel');
    req.flush({ data: alert({ id: 'a1', status: 'CANCELLED', cancelledAt: '2026-02-05T10:00:00Z' }), message: 'ok' });

    expect(fixture.componentInstance.alerts()[0].status).toBe('CANCELLED');
  });
});
