import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AxiosError } from 'axios'
import { Plus, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useAuth } from '@/hooks/useAuth'
import { searchProducts } from '@/services/productApi'
import { createOrder } from '@/services/orderApi'
import type { BaseResponse } from '@/types/api'
import type { CreateOrderItemInput } from '@/types/order'

interface OrderLine {
  key: number
  productId: string
  quantity: string
}

let nextKey = 1
function emptyLine(): OrderLine {
  return { key: nextKey++, productId: '', quantity: '1' }
}

interface CreateOrderDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function CreateOrderDialog({ open, onOpenChange }: CreateOrderDialogProps) {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [lines, setLines] = useState<OrderLine[]>([emptyLine()])

  useEffect(() => {
    if (open) setLines([emptyLine()])
  }, [open])

  const productsQuery = useQuery({
    queryKey: ['products', 'order-picker'],
    queryFn: () => searchProducts({ status: 'ACTIVE', inStockOnly: true, size: 50 }),
    enabled: open,
  })

  const mutation = useMutation({
    mutationFn: (items: CreateOrderItemInput[]) => {
      if (!user) throw new Error('Not authenticated')
      return createOrder({ customerId: Number(user.id), items })
    },
    onSuccess: (order) => {
      toast.success('Order created', { description: `Order ${order.orderNumber}` })
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      onOpenChange(false)
    },
    onError: (err) => {
      const axiosError = err as AxiosError<BaseResponse<unknown>>
      toast.error('Failed to create order', {
        description: axiosError.response?.data?.error?.message ?? 'Please check the form and try again.',
      })
    },
  })

  function updateLine(key: number, patch: Partial<OrderLine>) {
    setLines((current) => current.map((line) => (line.key === key ? { ...line, ...patch } : line)))
  }

  function addLine() {
    setLines((current) => [...current, emptyLine()])
  }

  function removeLine(key: number) {
    setLines((current) => (current.length > 1 ? current.filter((line) => line.key !== key) : current))
  }

  function handleSubmit() {
    const items: CreateOrderItemInput[] = []
    for (const line of lines) {
      const productId = Number(line.productId)
      const quantity = Number(line.quantity)
      if (!productId || !quantity || quantity < 1) {
        toast.error('Every line needs a product and a quantity of at least 1')
        return
      }
      items.push({ productId, quantity })
    }
    mutation.mutate(items)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>New order</DialogTitle>
          <DialogDescription>Add one or more products and submit to place the order.</DialogDescription>
        </DialogHeader>

        <div className="grid gap-3">
          {lines.map((line) => (
            <div key={line.key} className="flex items-center gap-2">
              <Select value={line.productId} onValueChange={(value) => updateLine(line.key, { productId: value })}>
                <SelectTrigger className="flex-1">
                  <SelectValue placeholder="Select a product" />
                </SelectTrigger>
                <SelectContent>
                  {productsQuery.data?.content.map((product) => (
                    <SelectItem key={product.id} value={String(product.id)}>
                      {product.name} — ${product.price.toFixed(2)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Input
                type="number"
                min={1}
                value={line.quantity}
                onChange={(event) => updateLine(line.key, { quantity: event.target.value })}
                className="w-20"
              />
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label="Remove line"
                onClick={() => removeLine(line.key)}
                disabled={lines.length === 1}
              >
                <Trash2 />
              </Button>
            </div>
          ))}
          <Button type="button" variant="outline" size="sm" className="w-fit" onClick={addLine}>
            <Plus /> Add item
          </Button>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="button" onClick={handleSubmit} disabled={mutation.isPending}>
            {mutation.isPending ? 'Placing order…' : 'Place order'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
