package me.zhulin.shopapi.service.impl;

import me.zhulin.shopapi.entity.Cart;
import me.zhulin.shopapi.entity.OrderMain;
import me.zhulin.shopapi.entity.ProductInOrder;
import me.zhulin.shopapi.entity.User;
import me.zhulin.shopapi.repository.CartRepository;
import me.zhulin.shopapi.repository.OrderRepository;
import me.zhulin.shopapi.repository.ProductInOrderRepository;
import me.zhulin.shopapi.repository.UserRepository;
import me.zhulin.shopapi.service.ProductService;
import me.zhulin.shopapi.service.UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.any;

/**
 * Jornada: finalizar a compra (checkout).
 * Nivel: UNITARIO. SUT: CartServiceImpl (fusao de carrinho e checkout).
 */
@RunWith(SpringRunner.class)
public class CartCheckoutUnitTest {

    @InjectMocks
    private CartServiceImpl cartService;

    @Mock
    private ProductService productService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductInOrderRepository productInOrderRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private UserService userService;

    // U5 - Regra de fusao: item ja no carrinho -> quantidades sao SOMADAS.
    @Test
    public void u5_mergeLocalCart_itemJaExiste_somaQuantidades() {
        // carrinho persistente do usuario ja tem o produto "1" com quantidade 10
        ProductInOrder existente = new ProductInOrder();
        existente.setProductId("1");
        existente.setCount(10);
        Set<ProductInOrder> produtosCarrinho = new HashSet<>();
        produtosCarrinho.add(existente);
        Cart cart = new Cart();
        cart.setProducts(produtosCarrinho);
        User user = new User();
        user.setCart(cart);

        // carrinho local (visitante) traz o MESMO produto "1" com quantidade 5
        ProductInOrder recebido = new ProductInOrder();
        recebido.setProductId("1");
        recebido.setCount(5);
        Set<ProductInOrder> carrinhoLocal = new HashSet<>();
        carrinhoLocal.add(recebido);

        cartService.mergeLocalCart(carrinhoLocal, user);

        // esperado: 10 + 5 = 15
        assertThat(existente.getCount(), is(15));
        Mockito.verify(cartRepository, Mockito.times(1)).save(cart);
    }

    // U6 - Fluxo do checkout: cria pedido, baixa estoque de cada item e salva o item.
    @Test
    public void u6_checkout_criaPedidoEBaixaEstoque() {
        ProductInOrder item = new ProductInOrder();
        item.setProductId("1");
        item.setCount(10);
        item.setProductPrice(BigDecimal.valueOf(10));
        Set<ProductInOrder> produtos = new HashSet<>();
        produtos.add(item);
        Cart cart = new Cart();
        cart.setProducts(produtos);
        User user = new User();
        user.setCart(cart);

        cartService.checkout(user);

        Mockito.verify(orderRepository, Mockito.times(1)).save(any(OrderMain.class));
        Mockito.verify(productService, Mockito.times(1)).decreaseStock("1", 10);
        Mockito.verify(productInOrderRepository, Mockito.times(1)).save(item);
    }
}
