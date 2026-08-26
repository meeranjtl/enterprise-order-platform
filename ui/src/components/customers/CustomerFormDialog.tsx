import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AxiosError } from 'axios'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { createCustomer, updateCustomer, type CustomerInput } from '@/services/customerApi'
import type { BaseResponse } from '@/types/api'
import type { Customer } from '@/types/customer'

const customerSchema = z.object({
  email: z.string().min(1, 'Email is required').email('Enter a valid email address'),
  firstName: z.string().min(2, 'First name must be at least 2 characters').max(100),
  lastName: z.string().max(100).optional().or(z.literal('')),
  phone: z.string().optional().or(z.literal('')),
  street: z.string().optional().or(z.literal('')),
  buildingNumber: z.string().optional().or(z.literal('')),
  city: z.string().min(1, 'City is required'),
  state: z.string().optional().or(z.literal('')),
  zipCode: z.string().optional().or(z.literal('')),
  country: z.string().min(1, 'Country is required'),
})

type CustomerFormValues = z.infer<typeof customerSchema>

const emptyValues: CustomerFormValues = {
  email: '',
  firstName: '',
  lastName: '',
  phone: '',
  street: '',
  buildingNumber: '',
  city: '',
  state: '',
  zipCode: '',
  country: '',
}

function valuesFromCustomer(customer: Customer): CustomerFormValues {
  return {
    email: customer.email,
    firstName: customer.firstName,
    lastName: customer.lastName ?? '',
    phone: customer.phone ?? '',
    street: customer.address?.street ?? '',
    buildingNumber: customer.address?.buildingNumber ?? '',
    city: customer.address?.city ?? '',
    state: customer.address?.state ?? '',
    zipCode: customer.address?.zipCode ?? '',
    country: customer.address?.country ?? '',
  }
}

function toPayload(values: CustomerFormValues): CustomerInput {
  return {
    email: values.email,
    firstName: values.firstName,
    lastName: values.lastName || undefined,
    phone: values.phone || undefined,
    address: {
      city: values.city,
      country: values.country,
      state: values.state || undefined,
      zipCode: values.zipCode || undefined,
      street: values.street || undefined,
      buildingNumber: values.buildingNumber || undefined,
    },
  }
}

interface CustomerFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  customer?: Customer
}

export function CustomerFormDialog({ open, onOpenChange, customer }: CustomerFormDialogProps) {
  const isEditing = customer !== undefined
  const queryClient = useQueryClient()

  const form = useForm<CustomerFormValues>({
    resolver: zodResolver(customerSchema),
    defaultValues: emptyValues,
  })

  useEffect(() => {
    if (open) {
      form.reset(customer ? valuesFromCustomer(customer) : emptyValues)
    }
  }, [open, customer, form])

  const mutation = useMutation({
    mutationFn: (values: CustomerFormValues) =>
      isEditing ? updateCustomer(customer.id, toPayload(values)) : createCustomer(toPayload(values)),
    onSuccess: () => {
      toast.success(isEditing ? 'Customer updated' : 'Customer created')
      queryClient.invalidateQueries({ queryKey: ['customers'] })
      onOpenChange(false)
    },
    onError: (err) => {
      const axiosError = err as AxiosError<BaseResponse<unknown>>
      toast.error(isEditing ? 'Update failed' : 'Create failed', {
        description: axiosError.response?.data?.error?.message ?? 'Please check the form and try again.',
      })
    },
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEditing ? 'Edit customer' : 'Create customer'}</DialogTitle>
          <DialogDescription>
            {isEditing ? 'Update this customer’s profile.' : 'Add a new customer record.'}
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
            className="grid max-h-[70svh] gap-4 overflow-y-auto px-1"
            noValidate
          >
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="firstName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>First name</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="lastName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Last name</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="email"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Email</FormLabel>
                    <FormControl>
                      <Input type="email" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="phone"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Phone</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="street"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Street</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="buildingNumber"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Building #</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <FormField
                control={form.control}
                name="city"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>City</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="state"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>State</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="zipCode"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>ZIP</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <FormField
              control={form.control}
              name="country"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Country</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Saving…' : 'Save'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
