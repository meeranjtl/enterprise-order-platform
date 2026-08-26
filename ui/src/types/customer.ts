export type CustomerStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'DELETED'

export interface Address {
  city: string
  state?: string
  zipCode?: string
  country: string
  street?: string
  buildingNumber?: string
}

export interface Customer {
  id: number
  email: string
  firstName: string
  lastName?: string
  phone?: string
  address?: Address
  status?: CustomerStatus
  createdAt?: string
  updatedAt?: string
}
