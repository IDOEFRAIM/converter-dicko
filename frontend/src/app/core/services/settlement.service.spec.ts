import { TestBed } from '@angular/core/testing';
import { SettlementService } from './settlement.service';

describe('SettlementService', () => {
  let service: SettlementService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SettlementService);
  });

  it('reports "not started" before payment is verified', () => {
    expect(service.deriveFromStatus('AWAITING_PAYMENT').stage).toBe('NOT_STARTED');
    expect(service.deriveFromStatus('PAYMENT_SUBMITTED').stage).toBe('NOT_STARTED');
  });

  it('reports "in progress" while the order is PROCESSING', () => {
    expect(service.deriveFromStatus('PROCESSING').stage).toBe('IN_PROGRESS');
  });

  it('reports "completed" once the order is COMPLETED', () => {
    expect(service.deriveFromStatus('COMPLETED').stage).toBe('COMPLETED');
  });
});
