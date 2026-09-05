import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { OrderTrackingPage } from './order-tracking.page';
import { OrderTracking, TrackingEvent } from '../../../core/models/tracking.model';

function event(code: TrackingEvent['code'], occurredAt = '2026-01-01T10:00:00Z'): TrackingEvent {
  return { code, status: null, occurredAt, label: code };
}

function tracking(currentStatus: string, timeline: TrackingEvent[]): OrderTracking {
  return {
    orderId: 'o1',
    currentStatus: currentStatus as OrderTracking['currentStatus'],
    createdAt: '2026-01-01T09:00:00Z',
    completedAt: null,
    timeline,
  };
}

describe('OrderTrackingPage', () => {
  let fixture: ComponentFixture<OrderTrackingPage>;
  let httpMock: HttpTestingController;

  async function createWith(data: OrderTracking) {
    await TestBed.configureTestingModule({
      imports: [OrderTrackingPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'o1' } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderTrackingPage);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/orders/o1/tracking').flush({ data, message: 'ok' });
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('uses the frontend code-to-label mapping, never the backend label field, for known codes', async () => {
    await createWith(tracking('PROCESSING', [event('ORDER_CREATED')]));
    const page = fixture.componentInstance;
    expect(page.labelFor(event('ORDER_CREATED'))).toBe('Transfert cree');
    expect(page.labelFor(event('ORDER_CREATED'))).not.toBe('ORDER_CREATED');
  });

  it('marks the last event as current while the order is not in a terminal status', async () => {
    const timeline = [event('ORDER_CREATED'), event('PAYMENT_SUBMITTED')];
    await createWith(tracking('PAYMENT_SUBMITTED', timeline));
    const page = fixture.componentInstance;
    expect(page.isCurrent(timeline[1], 1)).toBe(true);
    expect(page.isCurrent(timeline[0], 0)).toBe(false);
  });

  it('never marks the last event as current once the order reached a terminal status', async () => {
    const timeline = [event('ORDER_CREATED'), event('COMPLETED')];
    await createWith(tracking('COMPLETED', timeline));
    const page = fixture.componentInstance;
    expect(page.isCurrent(timeline[1], 1)).toBe(false);
  });

  it('treats negative codes as negative, never a refund/current event', async () => {
    const timeline = [event('ORDER_CREATED'), event('REJECTED')];
    await createWith(tracking('REJECTED', timeline));
    const page = fixture.componentInstance;
    expect(page.isNegative(timeline[1])).toBe(true);
    expect(page.isCurrent(timeline[1], 1)).toBe(false);
    expect(page.isRefund(timeline[1])).toBe(false);
  });

  it('identifies refund events distinctly, never as a negative/failure event', async () => {
    const timeline = [event('COMPLETED'), event('REFUND_PROCESSED')];
    await createWith(tracking('COMPLETED', timeline));
    const page = fixture.componentInstance;
    expect(page.isRefund(timeline[1])).toBe(true);
    expect(page.isNegative(timeline[1])).toBe(false);
  });

  it('records a client-side last-updated timestamp after a successful load', async () => {
    await createWith(tracking('COMPLETED', [event('COMPLETED')]));
    expect(fixture.componentInstance.lastUpdatedAt()).not.toBeNull();
  });
});
