import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AxiosError } from 'axios'
import { Pencil, Plus, Tags, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'

import { DataTablePagination } from '@/components/DataTablePagination'
import { StatusBadge } from '@/components/StatusBadge'
import { CategoryManagerDialog } from '@/components/products/CategoryManagerDialog'
import { ProductFormDialog } from '@/components/products/ProductFormDialog'
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
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useAuth } from '@/hooks/useAuth'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { deleteProduct, listCategories, searchProducts } from '@/services/productApi'
import type { BaseResponse } from '@/types/api'
import type { Product } from '@/types/product'

const PAGE_SIZE = 10
const ALL_CATEGORIES = 'all'
const ALL_STATUSES = 'all'

const currencyFormatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

export default function ProductsPage() {
  const { hasRole } = useAuth()
  const isAdmin = hasRole('ADMIN')
  const queryClient = useQueryClient()

  const [name, setName] = useState('')
  const [categoryId, setCategoryId] = useState(ALL_CATEGORIES)
  const [status, setStatus] = useState(ALL_STATUSES)
  const [minPrice, setMinPrice] = useState('')
  const [maxPrice, setMaxPrice] = useState('')
  const [page, setPage] = useState(0)

  const [formOpen, setFormOpen] = useState(false)
  const [editingProduct, setEditingProduct] = useState<Product | undefined>(undefined)
  const [categoriesOpen, setCategoriesOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<Product | null>(null)

  const debouncedName = useDebouncedValue(name)
  const debouncedMinPrice = useDebouncedValue(minPrice)
  const debouncedMaxPrice = useDebouncedValue(maxPrice)

  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: listCategories })

  const productsQuery = useQuery({
    queryKey: [
      'products',
      { name: debouncedName, categoryId, status, minPrice: debouncedMinPrice, maxPrice: debouncedMaxPrice, page },
    ],
    queryFn: () =>
      searchProducts({
        name: debouncedName || undefined,
        categoryId: categoryId === ALL_CATEGORIES ? undefined : Number(categoryId),
        status: status === ALL_STATUSES ? undefined : status,
        minPrice: debouncedMinPrice ? Number(debouncedMinPrice) : undefined,
        maxPrice: debouncedMaxPrice ? Number(debouncedMaxPrice) : undefined,
        page,
        size: PAGE_SIZE,
      }),
    placeholderData: (previous) => previous,
  })

  function withPageReset<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value)
      setPage(0)
    }
  }

  const deleteMutation = useMutation({
    mutationFn: (product: Product) => deleteProduct(product.id),
    onSuccess: () => {
      toast.success('Product deactivated')
      queryClient.invalidateQueries({ queryKey: ['products'] })
      setDeleteTarget(null)
    },
    onError: (err) => {
      const axiosError = err as AxiosError<BaseResponse<unknown>>
      toast.error('Failed to deactivate product', {
        description: axiosError.response?.data?.error?.message,
      })
    },
  })

  function openCreate() {
    setEditingProduct(undefined)
    setFormOpen(true)
  }

  function openEdit(product: Product) {
    setEditingProduct(product)
    setFormOpen(true)
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Products</h1>
          <p className="text-sm text-muted-foreground">Browse the catalog across every category.</p>
        </div>
        {isAdmin && (
          <div className="flex shrink-0 gap-2">
            <Button variant="outline" onClick={() => setCategoriesOpen(true)}>
              <Tags /> Categories
            </Button>
            <Button onClick={openCreate}>
              <Plus /> Add product
            </Button>
          </div>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Filters</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <Input
            placeholder="Search by name…"
            value={name}
            onChange={(event) => withPageReset(setName)(event.target.value)}
            className="lg:col-span-2"
          />
          <Select value={categoryId} onValueChange={withPageReset(setCategoryId)}>
            <SelectTrigger className="w-full">
              <SelectValue placeholder="All categories" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_CATEGORIES}>All categories</SelectItem>
              {categoriesQuery.data?.content.map((category) => (
                <SelectItem key={category.id} value={String(category.id)}>
                  {category.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={status} onValueChange={withPageReset(setStatus)}>
            <SelectTrigger className="w-full">
              <SelectValue placeholder="All statuses" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_STATUSES}>All statuses</SelectItem>
              <SelectItem value="ACTIVE">Active</SelectItem>
              <SelectItem value="OUT_OF_STOCK">Out of stock</SelectItem>
              <SelectItem value="INACTIVE">Inactive</SelectItem>
              <SelectItem value="DISCONTINUED">Discontinued</SelectItem>
            </SelectContent>
          </Select>
          <div className="flex gap-2">
            <Input
              type="number"
              min={0}
              placeholder="Min $"
              value={minPrice}
              onChange={(event) => withPageReset(setMinPrice)(event.target.value)}
            />
            <Input
              type="number"
              min={0}
              placeholder="Max $"
              value={maxPrice}
              onChange={(event) => withPageReset(setMaxPrice)(event.target.value)}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="flex flex-col gap-3">
          {productsQuery.isLoading && (
            <div className="flex flex-col gap-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-14 w-full" />
              ))}
            </div>
          )}

          {productsQuery.isError && (
            <p className="py-8 text-center text-sm text-destructive">Failed to load products. Please try again.</p>
          )}

          {productsQuery.data?.content.length === 0 && (
            <p className="py-8 text-center text-sm text-muted-foreground">No products match these filters.</p>
          )}

          {/* Stacked cards below sm; table from sm up — same data, two renderings. */}
          {productsQuery.data && productsQuery.data.content.length > 0 && (
            <div className="flex flex-col gap-2 sm:hidden">
              {productsQuery.data.content.map((product) => (
                <div key={product.id} className="flex flex-col gap-1 rounded-lg border p-3 text-sm">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-medium">{product.name}</span>
                    <StatusBadge status={product.status} />
                  </div>
                  <div className="flex items-center justify-between text-muted-foreground">
                    <span className="font-mono text-xs">{product.sku}</span>
                    <span>{product.categoryName ?? '—'}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="font-medium">{currencyFormatter.format(product.price)}</span>
                    <span className="text-muted-foreground">{product.stockQuantity} in stock</span>
                  </div>
                  {isAdmin && (
                    <div className="mt-1 flex justify-end gap-2">
                      <Button variant="outline" size="sm" onClick={() => openEdit(product)}>
                        <Pencil /> Edit
                      </Button>
                      <Button variant="outline" size="sm" onClick={() => setDeleteTarget(product)}>
                        <Trash2 /> Deactivate
                      </Button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}

          {productsQuery.data && productsQuery.data.content.length > 0 && (
            <div className="hidden overflow-x-auto sm:block">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>SKU</TableHead>
                    <TableHead>Name</TableHead>
                    <TableHead>Category</TableHead>
                    <TableHead className="text-right">Price</TableHead>
                    <TableHead className="text-right">Stock</TableHead>
                    <TableHead>Status</TableHead>
                    {isAdmin && <TableHead className="text-right">Actions</TableHead>}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {productsQuery.data.content.map((product) => (
                    <TableRow key={product.id}>
                      <TableCell className="font-mono text-xs">{product.sku}</TableCell>
                      <TableCell className="font-medium">{product.name}</TableCell>
                      <TableCell>{product.categoryName ?? '—'}</TableCell>
                      <TableCell className="text-right">{currencyFormatter.format(product.price)}</TableCell>
                      <TableCell className="text-right">{product.stockQuantity}</TableCell>
                      <TableCell>
                        <StatusBadge status={product.status} />
                      </TableCell>
                      {isAdmin && (
                        <TableCell className="text-right">
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label="Edit product"
                            onClick={() => openEdit(product)}
                          >
                            <Pencil />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label="Deactivate product"
                            onClick={() => setDeleteTarget(product)}
                          >
                            <Trash2 />
                          </Button>
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}

          {productsQuery.data && (
            <DataTablePagination
              page={productsQuery.data.number}
              totalPages={productsQuery.data.totalPages}
              totalElements={productsQuery.data.totalElements}
              onPageChange={setPage}
            />
          )}
        </CardContent>
      </Card>

      {isAdmin && (
        <>
          <ProductFormDialog open={formOpen} onOpenChange={setFormOpen} product={editingProduct} />
          <CategoryManagerDialog open={categoriesOpen} onOpenChange={setCategoriesOpen} />

          <AlertDialog open={deleteTarget !== null} onOpenChange={(next) => !next && setDeleteTarget(null)}>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Deactivate “{deleteTarget?.name}”?</AlertDialogTitle>
                <AlertDialogDescription>
                  This discontinues the product — it stays in past orders but won't be sold going forward.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>Keep product</AlertDialogCancel>
                <AlertDialogAction
                  variant="destructive"
                  onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget)}
                  disabled={deleteMutation.isPending}
                >
                  {deleteMutation.isPending ? 'Deactivating…' : 'Deactivate'}
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </>
      )}
    </div>
  )
}
