package me.zhulin.shopapi.service.impl;

import me.zhulin.shopapi.entity.ProductInfo;
import me.zhulin.shopapi.exception.MyException;
import me.zhulin.shopapi.repository.ProductInfoRepository;
import me.zhulin.shopapi.service.CategoryService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit4.SpringRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

/**
 * Jornada: finalizar a compra (checkout) - regra de disponibilidade de estoque.
 * Nivel: UNITARIO. Tecnicas: analise de valor-limite e particao de equivalencia.
 * SUT: ProductServiceImpl.decreaseStock, com estoque inicial = 10.
 */
@RunWith(SpringRunner.class)
public class CheckoutStockUnitTest {

    @InjectMocks
    private ProductServiceImpl productService;

    @Mock
    private ProductInfoRepository productInfoRepository;

    @Mock
    private CategoryService categoryService;

    private ProductInfo productInfo;

    @Before
    public void setUp() {
        productInfo = new ProductInfo();
        productInfo.setProductId("1");
        productInfo.setProductStock(10);
        productInfo.setProductStatus(1);
    }

    // U1 - EP valida / vizinho de baixo: comprar 9 de 10 -> estoque fica 1
    @Test
    public void u1_quantidadeMenorQueEstoque_atualizaEstoque() {
        when(productInfoRepository.findByProductId("1")).thenReturn(productInfo);

        productService.decreaseStock("1", 9);

        assertThat(productInfo.getProductStock(), is(1));
        Mockito.verify(productInfoRepository, Mockito.times(1)).save(productInfo);
    }

    // U2 - A3 valor-limite: comprar 10 de 10 -> estoque DEVERIA zerar.
    // Este teste REVELA o defeito D1 (hoje lanca PRODUCT_NOT_ENOUGH).
    @Test
    public void u2_quantidadeIgualAoEstoque_deveZerar() {
        when(productInfoRepository.findByProductId("1")).thenReturn(productInfo);

        productService.decreaseStock("1", 10);

        assertThat(productInfo.getProductStock(), is(0));
        Mockito.verify(productInfoRepository, Mockito.times(1)).save(productInfo);
    }

    // U3 - EP invalida / vizinho de cima: comprar 11 de 10 -> nao ha estoque
    @Test(expected = MyException.class)
    public void u3_quantidadeMaiorQueEstoque_lancaNotEnough() {
        when(productInfoRepository.findByProductId("1")).thenReturn(productInfo);

        productService.decreaseStock("1", 11);
    }

    // U4 - A2 produto inexistente -> PRODUCT_NOT_EXIST
    @Test(expected = MyException.class)
    public void u4_produtoInexistente_lancaNotExist() {
        when(productInfoRepository.findByProductId("1")).thenReturn(null);

        productService.decreaseStock("1", 5);
    }
}
