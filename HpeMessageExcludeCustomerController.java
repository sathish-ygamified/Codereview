/**
 *
 */
package com.hpe.controllers.pages;


import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractPageController;

import java.io.UnsupportedEncodingException;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.hpe.core.service.message.impl.DefaultHpeMessageService;
import com.hpe.facades.message.HpeMessageFacade;


/**
 * @author TE395038
 *
 */
@Controller
@RequestMapping(value = "/excludemessage")
public class HpeMessageExcludeCustomerController extends AbstractPageController
{
	private static final Logger LOG = Logger.getLogger(DefaultHpeMessageService.class);

	@Resource(name = "hpeMessageFacade")
	private HpeMessageFacade hpeMessageFacade;

	@PostMapping
	public String excludeMessage(@RequestParam("messageId") final String messageId, final Model model,
			final HttpServletRequest request, final HttpServletResponse response) throws UnsupportedEncodingException
	{
		final boolean b = hpeMessageFacade.addMessageInExcludedList(messageId);
		LOG.debug("Message with id " + messageId + " added in customer's excluded list result = " + b);
		return REDIRECT_PREFIX + "/";
	}
}
