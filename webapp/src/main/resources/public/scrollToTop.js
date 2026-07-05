



/**
 * Scroll to top functionality
 */


//Corresponding HTML
//<button class="scroll-to-top-btn hidden">
//  &#8679;
//  Top
//</button>


function instantiateScrollToTopEvenListener() {
  const scrollToTopButton = document.getElementsByClassName("scroll-to-top-btn")[0];

  scrollToTopButton.addEventListener("click", () => {
    window.scrollTo({top: 0, left: 0, behavior: "smooth"});
  });

  scrollToTopButton.addEventListener("mouseover", () => {
    scrollToTopButton.classList.add("opacity-full");
  })

  scrollToTopButton.addEventListener("mouseout", () => {
    scrollToTopButton.classList.remove("opacity-full");
  })

  window.addEventListener("scroll", (e) => {
    const scrollTop = document.body.scrollTop

    if (scrollTop > .5 * window.innerHeight) {
      scrollToTopButton.classList.remove("hidden")
    } else {
      scrollToTopButton.classList.add("hidden")
    }

  })
}
