/**
 *
 */
package com.hpe.controllers.pages;

import de.hybris.platform.acceleratorservices.controllers.page.PageType;
import de.hybris.platform.acceleratorservices.data.RequestContextData;
import de.hybris.platform.acceleratorservices.storefront.data.MetaElementData;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.ThirdPartyConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractCategoryPageController;
import de.hybris.platform.acceleratorstorefrontcommons.util.MetaSanitizerUtil;
import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.cms2.model.pages.CategoryPageModel;
import de.hybris.platform.commercefacades.product.data.CategoryData;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.search.data.SearchQueryData;
import de.hybris.platform.commercefacades.search.data.SearchStateData;
import de.hybris.platform.commerceservices.search.facetdata.ProductCategorySearchPageData;
import de.hybris.platform.commerceservices.search.pagedata.PageableData;
import de.hybris.platform.commerceservices.url.UrlResolver;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.dto.converter.ConversionException;
import de.hybris.platform.servicelayer.session.SessionService;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.ui.Model;

import com.hpe.core.model.PLPGuidedSellingModel;
import com.hpe.facades.populators.HPEPLPGuidedSellingPopulator;
import com.hpe.facades.product.Breadcrumb;
import com.hpe.facades.product.data.search.pagedata.PLPGuidedSellingData;
import com.hpe.facades.product.impl.HPESearchBreadcrumbBuilder;
import com.hpe.facades.util.GenericUtil;
import com.hpe.facades.wishlist.HPEWishlistFacade;
import com.hpe.storefront.util.HPEAnalyticsUtil;

import atg.taglib.json.util.JSONArray;
import atg.taglib.json.util.JSONException;
import atg.taglib.json.util.JSONObject;


/**
 * @author DA20002251
 *
 */
public class AbstractHPECategoryPageController extends AbstractCategoryPageController
{
	private static final Logger LOGGER = Logger.getLogger(AbstractHPECategoryPageController.class);
	private static final String REQUESTURL = "requestUrl";


	@Resource
	private HPEPLPGuidedSellingPopulator plpGuidedSellingPopulator;

	@Resource(name = "hpeWishlist")
	private HPEWishlistFacade hpeWishlistFacade;

	@Resource(name = "hpeSearchBreadcrumbBuilder")
	private HPESearchBreadcrumbBuilder hpeSearchBreadcrumbBuilder;

	@Resource(name = "defaultHPEUrlResolver")
	private UrlResolver<CategoryModel> defaultHPEUrlResolver;

	@Resource(name = "sessionService")
	private SessionService sessionService;

	@Resource(name = "hpeAnalyticsUtil")
	private HPEAnalyticsUtil hpeAnalyticsUtil;

	@Resource(name = "configurationService")
	private ConfigurationService configurationService;

	protected String performSearchAndGetResultsPage(final String categoryCode, final String searchQuery, final int page, // NOSONAR
			final ShowMode showMode, final String sortCode, final Model model, final HttpServletRequest request,
			final HttpServletResponse response, final int pageSize, final String textSearch) throws UnsupportedEncodingException
	{
		final Collection<PLPGuidedSellingData> gsDataList = new ArrayList<>();
		sessionService.setAttribute(REQUESTURL, request.getRequestURL());
		final CategoryModel category = getCommerceCategoryService().getCategoryForCode(categoryCode);

		final String redirection = checkRequestUrl(request, response, defaultHPEUrlResolver.resolve(category));
		if (StringUtils.isNotEmpty(redirection))
		{
			return redirection;
		}

		final CategoryPageModel categoryPage = getCategoryPage(category);

		final CategorySearchEvaluator categorySearch = new CategorySearchEvaluator(categoryCode, searchQuery, page, showMode,
				sortCode, categoryPage, pageSize, textSearch);

		ProductCategorySearchPageData<SearchStateData, ProductData, CategoryData> searchPageData = null;
		try
		{
			categorySearch.doSearch();
			searchPageData = categorySearch.getSearchPageData();
		}
		catch (final ConversionException e) // NOSONAR
		{
			searchPageData = createEmptySearchResult(categoryCode);
		}

		final List<String> productCodesList = new ArrayList<>();

		for (int index = 0; index < searchPageData.getResults().size(); index++)
		{
			final String productCode = searchPageData.getResults().get(index).getCode();
			productCodesList.add(productCode);
		}

		final boolean showCategoriesOnly = categorySearch.isShowCategoriesOnly();

		storeCmsPageInModel(model, categorySearch.getCategoryPage());
		storeContinueUrl(request);

		final Set<PLPGuidedSellingModel> gsModels = category.getPLPGuidedSelling();

		for (final PLPGuidedSellingModel gsModel : gsModels)
		{
			final PLPGuidedSellingData gsData = new PLPGuidedSellingData();

			plpGuidedSellingPopulator.populate(gsModel, gsData);
			gsDataList.add(gsData);
		}
		final JSONObject responseDetailsJson = new JSONObject();
		final JSONArray jsonArray = new JSONArray();


		try
		{
			for (final PLPGuidedSellingData obj : gsDataList)
			{
				final JSONArray jsonArray1 = new JSONArray();
				final JSONObject formDetailsJson = new JSONObject();
				formDetailsJson.put("qaId", obj.getQaId().toString());
				formDetailsJson.put("sequenceId", obj.getSequenceId().toString());
				formDetailsJson.put("question", obj.getQuestion().toString());
				formDetailsJson.put("description", obj.getDescription().toString());
				for (final String ans : obj.getAnswers())
				{
					jsonArray1.add(ans);
				}
				formDetailsJson.put("answers", jsonArray1);
				formDetailsJson.put("indexAttr", obj.getIndexAttr().toString());
				jsonArray.add(formDetailsJson);
			}
			responseDetailsJson.put("guidedSelling", jsonArray);

		}
		catch (final JSONException e)
		{
			LOGGER.error(e);
		}




		populateModel(model, searchPageData, showMode);
		final List<Breadcrumb> breadCrumbs = hpeSearchBreadcrumbBuilder.getBreadcrumbs(categoryCode, searchPageData);
		model.addAttribute(WebConstants.BREADCRUMBS_KEY, breadCrumbs);
		model.addAttribute("showCategoriesOnly", Boolean.valueOf(showCategoriesOnly));
		model.addAttribute("categoryName", category.getName());
		model.addAttribute("pageType", PageType.CATEGORY.name());
		model.addAttribute("userLocation", getCustomerLocationService().getUserLocation());
		model.addAttribute("gsList", responseDetailsJson.toString());

		final Collection<String> pageSizes = GenericUtil.getSearchListPageSizes();

		model.addAttribute("pageSizes", pageSizes);

		int selectedPageSize = 0;

		if (pageSize == 0)
		{
			selectedPageSize = getSearchPageSize();

		}
		else
		{
			selectedPageSize = pageSize;
		}


		model.addAttribute("selectedPageSize", selectedPageSize);

		updatePageTitle(category, model);

		final RequestContextData requestContextData = getRequestContextData(request);
		requestContextData.setCategory(category);
		requestContextData.setSearch(searchPageData);

		if (searchQuery != null)
		{
			model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_FOLLOW);
		}

		final String metaKeywords = MetaSanitizerUtil.sanitizeKeywords(
				category.getKeywords().stream().map(keywordModel -> keywordModel.getKeyword()).collect(Collectors.toSet())); //NOSONAR
		final String metaDescription = MetaSanitizerUtil.sanitizeDescription(category.getDescription());
		setUpMetaData(model, metaKeywords, metaDescription);

		setUpMetaData(model, metaKeywords, metaDescription, category);
		model.addAttribute("canonical", sessionService.getAttribute(REQUESTURL).toString());
		final org.json.JSONObject productListJsonObject = hpeAnalyticsUtil.getProductListJsonObjectForAnalytics(searchPageData,
				breadCrumbs);
		model.addAttribute("productListJsonObject", productListJsonObject);

		return getViewPage(categorySearch.getCategoryPage());

	}

	protected void setUpMetaData(final Model model, final String metaKeywords, final String metaDescription,
			final CategoryModel category)
	{
		final List<MetaElementData> metadata = new LinkedList<>();
		metadata.add(createMetaElement("keywords", metaKeywords));
		//added for <meta tag attribute>
		metadata.add(createMetaElementProperty("og:url", sessionService.getAttribute(REQUESTURL).toString()));
		metadata.add(createMetaElementProperty("og:type", "Website"));
		metadata.add(
				createMetaElementProperty("og:title", getPageTitleResolver().resolveCategoryPageTitle(category).replace('|', '-')));
		metadata.add(createMetaElementProperty("og:description", metaDescription));
		metadata.add(createMetaElementProperty("og:image", (category.getPicture() != null) ? category.getPicture().getURL() : ""));
		model.addAttribute("metatags", metadata);
	}

	protected MetaElementData createMetaElementProperty(final String name, final String content)
	{
		final MetaElementData element = new MetaElementData();
		element.setName(name);
		element.setProperty(content);
		return element;
	}

	public class CategorySearchEvaluator extends AbstractCategoryPageController.CategorySearchEvaluator
	{

		/**
		 * @param categoryCode
		 * @param searchQuery
		 * @param page
		 * @param showMode
		 * @param sortCode
		 * @param categoryPage
		 */
		private final String categoryCode;
		private final SearchQueryData searchQueryData = new SearchQueryData();
		private boolean showCategoriesOnly;
		private final int page;
		private final ShowMode showMode;
		private final String sortCode;
		private CategoryPageModel categoryPage;
		private ProductCategorySearchPageData<SearchStateData, ProductData, CategoryData> searchPageData;

		private final int pageSize;
		private final String textSearch;

		public CategorySearchEvaluator(final String categoryCode, final String searchQuery, final int page, final ShowMode showMode,
				final String sortCode, final CategoryPageModel categoryPage, final int pageSize, final String textSearch)
		{
			super(categoryCode, searchQuery, page, showMode, sortCode, categoryPage);
			this.categoryCode = categoryCode;
			this.searchQueryData.setValue(searchQuery);
			this.page = page;
			this.showMode = showMode;
			this.sortCode = sortCode;
			this.categoryPage = categoryPage;
			this.pageSize = pageSize;
			this.textSearch = textSearch;
		}

		/**
		 * @return the searchPageData
		 */
		@Override
		public ProductCategorySearchPageData<SearchStateData, ProductData, CategoryData> getSearchPageData()
		{
			return searchPageData;
		}

		@Override
		public void doSearch()
		{

			int selectedPageSize = 0;

			if (categoryPage == null || !categoryHasDefaultPage(categoryPage))
			{
				// Load the default category page
				categoryPage = getDefaultCategoryPage();
			}
			/* Set the Search Query Data only when it is not empty */

			if (StringUtils.isNotEmpty(textSearch))
			{
				searchQueryData.setValue(textSearch);
			}

			final SearchStateData searchState = new SearchStateData();
			searchState.setQuery(searchQueryData);

			if (pageSize == 0)
			{
				selectedPageSize = getSearchPageSize();

			}
			else
			{
				selectedPageSize = pageSize;
			}
			final PageableData pageableData = createPageableData(page, selectedPageSize, sortCode, showMode);
			searchPageData = getProductSearchFacade().categorySearch(categoryCode, searchState, pageableData);
			//Encode SearchPageData
			searchPageData = (ProductCategorySearchPageData) encodeSearchPageData(searchPageData);

			for (final ProductData product : searchPageData.getResults())
			{
				product.setBookmarkFlag(hpeWishlistFacade.isBookmarked(product.getCode()));
			}
		}
	}

	/**
	 * This method is used to prepare a JSON object and set in the JSP page. The JSON Object is parsed by Tealium Tag
	 * Manager tool for analytics.
	 */
	private JSONObject getProductListJsonObjectForAnalytics(
			final ProductCategorySearchPageData<SearchStateData, ProductData, CategoryData> searchPageData,
			final List<Breadcrumb> breadCrumbs)
	{
		final JSONObject jsonObject = new JSONObject();

		final List productInfoList = new ArrayList();
		final JSONArray jsonArray = new JSONArray();

		if (breadCrumbs != null)
		{
			for (int index = 0; index < breadCrumbs.size(); index++)
			{
				jsonArray.add(breadCrumbs.get(index).getName());
			}
		}
		try
		{
			if (searchPageData != null && searchPageData.getResults() != null)
			{
				for (int index = 0; index < searchPageData.getResults().size(); index++)
				{
					final String productCode = searchPageData.getResults().get(index).getCode();
					final String productName = searchPageData.getResults().get(index).getName();
					final HashMap productInfo = new HashMap();

					productInfo.put("productID", productCode);
					productInfo.put("productName", productName);

					productInfoList.add(productInfo);
				}

				jsonObject.put("productInfo", productInfoList);
				jsonObject.put("navigationCategory", jsonArray);
			}
		}
		catch (final JSONException e)
		{
			LOGGER.error(e);
		}

		return jsonObject;
	}


}
