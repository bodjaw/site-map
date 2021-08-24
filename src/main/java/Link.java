public class Link implements Comparable<Link> {

    private final String link;
    private final int level;

    public Link(String link, int level) {
        this.link = link;
        this.level = level;
    }

    public String getLink() {
        return link;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public String toString() {
        return withTab();
    }

    private String withTab() {
        return "/t".repeat(level);
    }

    @Override
    public int compareTo(Link o) {
        return getLink().compareTo(o.getLink());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Link link1 = (Link) o;
        if (level != link1.level) return false;
        return link.equals(link1.link);
    }

    @Override
    public int hashCode() {
        int result = link.hashCode();
        result = 31 * result + level;
        return result;
    }
}
