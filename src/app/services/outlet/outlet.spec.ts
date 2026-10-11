import { TestBed } from '@angular/core/testing';

import { Outlet } from './outlet';

describe('Outlet', () => {
  let service: Outlet;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Outlet);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
