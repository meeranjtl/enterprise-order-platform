import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { CustomerFormDialog } from '@/components/customers/CustomerFormDialog'
import { DataTablePagination } from '@/components/DataTablePagination'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { deleteCustomer, searchCustomers } from '@/services/customerApi'
import type { Customer } from '@/types/customer'

const PAGE_SIZE = 10
const ALL_STATUSES = 'all'

export default function CustomersPage() {
  const [email, setEmail] = useState('')
  const [status, setStatus] = useState(ALL_STATUSES)
  const [page, setPage] = useState(0)
  const [formOpen, setFormOpen] = useState(false)
  const [editingCustomer, setEditingCustomer] = useState<Customer | undefined>(undefined)
  const [deletingCustomer, setDeletingCustomer] = useState<Customer | undefined>(undefined)

  const debouncedEmail = useDebouncedValue(email)
  const queryClient = useQueryClient()

  const customersQuery = useQuery({
    queryKey: ['customers', { email: debouncedEmail, status, page }],
    queryFn: () =>
      searchCustomers({
        email: debouncedEmail || undefined,
        status: status === ALL_STATUSES ? undefined : status,
        page,
        size: PAGE_SIZE,
      }),
    placeholderData: (previous) => previous,
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteCustomer(id),
    onSuccess: () => {
      toast.success('Customer deleted')
      queryClient.invalidateQueries({ queryKey: ['customers'] })
      setDeletingCustomer(undefined)
    },
    onError: () => {
      toast.error('Failed to delete customer')
    },
  })

  function openCreate() {
    setEditingCustomer(undefined)
    setFormOpen(true)
  }

  function openEdit(customer: Customer) {
    setEditingCustomer(customer)
    setFormOpen(true)
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Customers</h1>
          <p className="text-sm text-muted-foreground">Manage customer accounts across the platform.</p>
        </div>
        <Button onClick={openCreate}>
          <Plus /> New customer
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Filters</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Input
            placeholder="Search by email…"
            value={email}
            onChange={(event) => {
              setEmail(event.target.value)
              setPage(0)
            }}
            className="lg:col-span-2"
          />
          <Select
            value={status}
            onValueChange={(value) => {
              setStatus(value)
              setPage(0)
            }}
          >
            <SelectTrigger className="w-full">
              <SelectValue placeholder="All statuses" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_STATUSES}>All statuses</SelectItem>
              <SelectItem value="ACTIVE">Active</SelectItem>
              <SelectItem value="INACTIVE">Inactive</SelectItem>
              <SelectItem value="SUSPENDED">Suspended</SelectItem>
              <SelectItem value="DELETED">Deleted</SelectItem>
            </SelectContent>
          </Select>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="flex flex-col gap-3">
          {customersQuery.isLoading && (
            <div className="flex flex-col gap-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-16 w-full" />
              ))}
            </div>
          )}

          {customersQuery.isError && (
            <p className="py-8 text-center text-sm text-destructive">Failed to load customers. Please try again.</p>
          )}

          {customersQuery.data?.content.length === 0 && (
            <p className="py-8 text-center text-sm text-muted-foreground">No customers match these filters.</p>
          )}

          {/* Stacked cards below sm; table from sm up — same data, two renderings. */}
          {customersQuery.data && customersQuery.data.content.length > 0 && (
            <div className="flex flex-col gap-2 sm:hidden">
              {customersQuery.data.content.map((customer) => (
                <div key={customer.id} className="flex flex-col gap-1 rounded-lg border p-3 text-sm">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-medium">
                      {customer.firstName} {customer.lastName}
                    </span>
                    {customer.status ? <StatusBadge status={customer.status} /> : null}
                  </div>
                  <p className="text-muted-foreground">{customer.email}</p>
                  <div className="flex items-center justify-between text-muted-foreground">
                    <span>{customer.phone ?? '—'}</span>
                    <span>{[customer.address?.city, customer.address?.country].filter(Boolean).join(', ') || '—'}</span>
                  </div>
                  <div className="mt-1 flex justify-end gap-1">
                    <Button variant="ghost" size="icon-sm" aria-label="Edit customer" onClick={() => openEdit(customer)}>
                      <Pencil />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="Delete customer"
                      onClick={() => setDeletingCustomer(customer)}
                    >
                      <Trash2 className="text-destructive" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {customersQuery.data && customersQuery.data.content.length > 0 && (
            <div className="hidden overflow-x-auto sm:block">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Email</TableHead>
                    <TableHead>Phone</TableHead>
                    <TableHead>Location</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {customersQuery.data.content.map((customer) => (
                    <TableRow key={customer.id}>
                      <TableCell className="font-medium">
                        {customer.firstName} {customer.lastName}
                      </TableCell>
                      <TableCell className="text-muted-foreground">{customer.email}</TableCell>
                      <TableCell className="text-muted-foreground">{customer.phone ?? '—'}</TableCell>
                      <TableCell className="text-muted-foreground">
                        {[customer.address?.city, customer.address?.country].filter(Boolean).join(', ') || '—'}
                      </TableCell>
                      <TableCell>{customer.status ? <StatusBadge status={customer.status} /> : '—'}</TableCell>
                      <TableCell className="text-right">
                        <Button variant="ghost" size="icon-sm" aria-label="Edit customer" onClick={() => openEdit(customer)}>
                          <Pencil />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          aria-label="Delete customer"
                          onClick={() => setDeletingCustomer(customer)}
                        >
                          <Trash2 className="text-destructive" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}

          {customersQuery.data && (
            <DataTablePagination
              page={customersQuery.data.number}
              totalPages={customersQuery.data.totalPages}
              totalElements={customersQuery.data.totalElements}
              onPageChange={setPage}
            />
          )}
        </CardContent>
      </Card>

      <CustomerFormDialog open={formOpen} onOpenChange={setFormOpen} customer={editingCustomer} />

      <AlertDialog open={deletingCustomer !== undefined} onOpenChange={(open) => !open && setDeletingCustomer(undefined)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete customer?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently remove {deletingCustomer?.firstName} {deletingCustomer?.lastName}'s account. This
              can't be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={() => deletingCustomer && deleteMutation.mutate(deletingCustomer.id)}
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
